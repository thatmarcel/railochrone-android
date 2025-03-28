package com.thatmarcel.apps.railochrone.ui.activities

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.android.gestures.StandardScaleGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.MapView
import com.mapbox.maps.ViewAnnotationOptions
import com.mapbox.maps.extension.style.expressions.dsl.generated.get
import com.mapbox.maps.extension.style.expressions.dsl.generated.literal
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.OnScaleListener
import com.mapbox.maps.plugin.gestures.addOnScaleListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.maps.plugin.viewport.viewport
import com.mapbox.maps.viewannotation.geometry
import com.thatmarcel.apps.railochrone.helpers.AppUpdateChecker
import com.thatmarcel.apps.railochrone.helpers.types.LivePositionInfo
import com.thatmarcel.apps.railochrone.R
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {
    private lateinit var mapView: MapView

    val okHttpClient = OkHttpClient()

    val livePositionInfos: MutableList<LivePositionInfo> = mutableListOf()
    var livePositionAnnotationViews: MutableList<View> = mutableListOf()

    val compactPointStyleAbsoluteZoomThreshold = 7.5
    val compactPointStyleConditionalZoomThreshold = 11.75
    val compactPointStyleConditionalPointCountThreshold = 30
    val compactPointViewScale = 0.7f

    val ultraCompactPointStyleAbsoluteZoomThreshold = 6.5
    val ultraCompactPointViewScale = 0.5f

    val viewportCoordinatePadding = 0.25

    @SuppressLint("RtlHardcoded", "IncorrectNumberOfArgumentsInExpression")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.activity_main_map_view)

        mapView.scalebar.enabled = false
        mapView.compass.enabled = false

        mapView.logo.position = Gravity.BOTTOM or Gravity.LEFT
        mapView.attribution.position = Gravity.BOTTOM or Gravity.LEFT

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            mapView.logo.marginBottom += systemBars.bottom + 2
            mapView.logo.marginLeft += 24
            mapView.attribution.marginBottom += systemBars.bottom + 2
            mapView.attribution.marginLeft += 24

            insets
        }

        requestLocationPermission()
        showLocationPuck()

        loadMapStyle(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && resources.configuration.isNightModeActive)

        updateRealtimeDataAfterDelay()

        mapView.mapboxMap.addOnScaleListener(object : OnScaleListener {
            override fun onScale(detector: StandardScaleGestureDetector) {
                val shouldUseUltraCompactStyle = mapView.mapboxMap.cameraState.zoom < ultraCompactPointStyleAbsoluteZoomThreshold
                val shouldUseCompactPointStyle = (
                    !shouldUseUltraCompactStyle &&
                    mapView.mapboxMap.cameraState.zoom < compactPointStyleAbsoluteZoomThreshold ||
                    (
                        mapView.mapboxMap.cameraState.zoom < compactPointStyleConditionalZoomThreshold &&
                        livePositionAnnotationViews.size > compactPointStyleConditionalPointCountThreshold

                    )
                )

                livePositionAnnotationViews.forEach {
                    val cardView: MaterialCardView = it.findViewById(R.id.live_position_marker_card_view)
                    val pointView: ImageView = it.findViewById(R.id.live_position_marker_point_view)

                    if (shouldUseCompactPointStyle) {
                        cardView.visibility = View.INVISIBLE
                        pointView.scaleX = compactPointViewScale
                        pointView.scaleY = compactPointViewScale
                    } else if (shouldUseUltraCompactStyle) {
                        cardView.visibility = View.INVISIBLE
                        pointView.scaleX = ultraCompactPointViewScale
                        pointView.scaleY = ultraCompactPointViewScale
                    } else {
                        cardView.visibility = View.VISIBLE
                        pointView.scaleX = 1.0f
                        pointView.scaleY = 1.0f
                    }
                }
            }

            override fun onScaleBegin(detector: StandardScaleGestureDetector) { }

            override fun onScaleEnd(detector: StandardScaleGestureDetector) { }
        })
    }

    private fun loadMapStyle(isNightModeActive: Boolean) {
        mapView.mapboxMap.loadStyle(
            if (isNightModeActive) "mapbox://styles/thatmarcelbraun/cm8paot1z006a01sigo7m2nj7"
            else "mapbox://styles/thatmarcelbraun/cm8pafb0g006e01sa4ek3bre9"
        ) {
            it.addSource(geoJsonSource("lines") {
                data("https://livekarte.vvs.de/geojson/linesV6.geojson")
            })

            it.addLayer(lineLayer("bus", "lines") {
                lineCap(LineCap.Companion.ROUND)
                lineJoin(LineJoin.Companion.ROUND)
                lineOpacity(0.8)
                lineWidth(4.5)
                lineColor("#b32e2d")
                filter(
                    Expression.Companion.any(
                        Expression.Companion.eq(get("branch"), literal(30)),
                        Expression.Companion.eq(get("branch"), literal(31)),
                        Expression.Companion.eq(get("branch"), literal(32)),
                        Expression.Companion.eq(get("branch"), literal(33)),
                        Expression.Companion.eq(get("branch"), literal(34)),
                        Expression.Companion.eq(get("branch"), literal(35)),
                        Expression.Companion.eq(get("branch"), literal(36)),
                        Expression.Companion.eq(get("branch"), literal(37)),
                        Expression.Companion.eq(get("branch"), literal(38)),
                        Expression.Companion.eq(get("branch"), literal(39))
                    )
                )
            })

            it.addLayer(lineLayer("u-train", "lines") {
                lineCap(LineCap.Companion.ROUND)
                lineJoin(LineJoin.Companion.ROUND)
                lineOpacity(0.8)
                lineWidth(4.5)
                lineColor("#418cc3")
                filter(
                    Expression.Companion.any(
                        Expression.Companion.eq(get("branch"), literal(20)),
                        Expression.Companion.eq(get("branch"), literal(21)),
                        Expression.Companion.eq(get("branch"), literal(22)),
                        Expression.Companion.eq(get("branch"), literal(23)),
                        Expression.Companion.eq(get("branch"), literal(24)),
                        Expression.Companion.eq(get("branch"), literal(25)),
                        Expression.Companion.eq(get("branch"), literal(26)),
                        Expression.Companion.eq(get("branch"), literal(27)),
                        Expression.Companion.eq(get("branch"), literal(28)),
                        Expression.Companion.eq(get("branch"), literal(29))
                    )
                )
            })

            it.addLayer(lineLayer("s-train", "lines") {
                lineCap(LineCap.Companion.ROUND)
                lineJoin(LineJoin.Companion.ROUND)
                lineOpacity(0.8)
                lineWidth(4.5)
                lineColor("#6cb146")
                filter(
                    Expression.Companion.any(
                        Expression.Companion.eq(get("branch"), literal(10)),
                        Expression.Companion.eq(get("branch"), literal(11)),
                        Expression.Companion.eq(get("branch"), literal(12)),
                        Expression.Companion.eq(get("branch"), literal(13)),
                        Expression.Companion.eq(get("branch"), literal(14)),
                        Expression.Companion.eq(get("branch"), literal(15)),
                        Expression.Companion.eq(get("branch"), literal(16)),
                        Expression.Companion.eq(get("branch"), literal(17)),
                        Expression.Companion.eq(get("branch"), literal(18)),
                        Expression.Companion.eq(get("branch"), literal(19))
                    )
                )
            })
        }
    }

    private fun updateRealtimeDataAfterDelay() {
        Handler(Looper.getMainLooper()).postDelayed({
            updateRealtimeData()

            if (!isDestroyed) {
                updateRealtimeDataAfterDelay()
            }
        }, 1000)
    }

    private fun updateRealtimeData() {
        val cameraState = mapView.mapboxMap.cameraState

        val coordinateBounds = mapView.mapboxMap.coordinateBoundsForCamera(
            CameraOptions.Builder()
                .zoom(cameraState.zoom)
                .pitch(cameraState.pitch)
                .bearing(cameraState.bearing)
                .center(cameraState.center)
                .padding(cameraState.padding)
                .build()
        )

        val latMin = min(
            coordinateBounds.northeast.latitude(),
            coordinateBounds.southwest.latitude()
        ) - viewportCoordinatePadding
        val lonMin = min(
            coordinateBounds.northeast.longitude(),
            coordinateBounds.southwest.longitude()
        ) - viewportCoordinatePadding
        val latMax = max(
            coordinateBounds.northeast.latitude(),
            coordinateBounds.southwest.latitude()
        ) + viewportCoordinatePadding
        val lonMax = max(
            coordinateBounds.northeast.longitude(),
            coordinateBounds.southwest.longitude()
        ) + viewportCoordinatePadding

        val url = "https://livekarte.vvs.de/proxy/livepositions?latMin=${latMin}&lonMin=${lonMin}&latMax=${latMax}&lonMax=${lonMax}"

        val request = Request.Builder()
            .url(url)
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                val responseBodyString = response.body?.string() ?: return

                val newLivePositionInfos: List<LivePositionInfo> = Gson().fromJson(
                    responseBodyString,
                    object : TypeToken<List<LivePositionInfo>>() {}.type
                )

                Handler(Looper.getMainLooper()).post {
                    livePositionInfos
                        .filter { a -> newLivePositionInfos.none { b -> a.id == b.id } }
                        .forEach {
                            val index = livePositionInfos.indexOf(it)

                            mapView.viewAnnotationManager.removeViewAnnotation(livePositionAnnotationViews[index])
                            livePositionAnnotationViews.removeAt(index)

                            livePositionInfos.removeAt(index)
                        }

                    for (newLivePositionInfo in newLivePositionInfos) {
                        val prevPositionInfo = livePositionInfos.firstOrNull { it.id == newLivePositionInfo.id }

                        var annotationView: View

                        if (prevPositionInfo == null) {
                            annotationView = mapView.viewAnnotationManager.addViewAnnotation(
                                R.layout.live_position_marker,
                                ViewAnnotationOptions.Builder()
                                    .geometry(
                                        Point.fromLngLat(
                                        newLivePositionInfo.longitude,
                                        newLivePositionInfo.latitude
                                    ))
                                    .allowOverlap(true)
                                    .allowOverlapWithPuck(true)
                                    .build()
                            )

                            livePositionInfos.add(newLivePositionInfo)
                            livePositionAnnotationViews.add(annotationView)
                        } else {
                            val prevPositionIndex = livePositionInfos.indexOf(prevPositionInfo)

                            annotationView = livePositionAnnotationViews[prevPositionIndex]

                            if (
                                prevPositionInfo.longitude != newLivePositionInfo.longitude ||
                                prevPositionInfo.latitude != newLivePositionInfo.latitude
                            ) {
                                mapView.viewAnnotationManager.updateViewAnnotation(
                                    annotationView,
                                    ViewAnnotationOptions.Builder()
                                        .geometry(
                                            Point.fromLngLat(
                                            newLivePositionInfo.longitude,
                                            newLivePositionInfo.latitude)
                                        )
                                        .build()
                                )
                            }
                        }

                        val lineNameTextView: TextView = annotationView.findViewById(R.id.live_position_marker_line_name_text_view)
                        val directionTextView: TextView = annotationView.findViewById(R.id.live_position_marker_direction_text_view)
                        val pointView: ImageView = annotationView.findViewById(R.id.live_position_marker_point_view)
                        val cardView: MaterialCardView = annotationView.findViewById(R.id.live_position_marker_card_view)

                        lineNameTextView.text = newLivePositionInfo.line
                            .replace("S-Bahn ", "")
                            .replace("R-Bahn ", "")
                            .replace("Stadtbahn ", "")
                            .replace("Bus ", "")

                        directionTextView.text = newLivePositionInfo.direction

                        if (newLivePositionInfo.type == "Bus") {
                            pointView.setColorFilter(Color.rgb(179, 46, 45))
                            cardView.setCardBackgroundColor(Color.rgb(179, 46, 45))
                        } else if (newLivePositionInfo.type == "Stadtbahn") {
                            pointView.setColorFilter(Color.rgb(65, 140, 195))
                            cardView.setCardBackgroundColor(Color.rgb(65, 140, 195))
                        } else {
                            pointView.setColorFilter(Color.rgb(108, 177, 70))
                            cardView.setCardBackgroundColor(Color.rgb(108, 177, 70))
                        }

                        val shouldUseUltraCompactStyle = mapView.mapboxMap.cameraState.zoom < ultraCompactPointStyleAbsoluteZoomThreshold
                        val shouldUseCompactPointStyle = (
                            !shouldUseUltraCompactStyle &&
                            mapView.mapboxMap.cameraState.zoom < compactPointStyleAbsoluteZoomThreshold ||
                            (
                                mapView.mapboxMap.cameraState.zoom < compactPointStyleConditionalZoomThreshold &&
                                livePositionAnnotationViews.size > compactPointStyleConditionalPointCountThreshold
                            )
                        )

                        if (shouldUseCompactPointStyle) {
                            cardView.visibility = View.INVISIBLE
                            pointView.scaleX = compactPointViewScale
                            pointView.scaleY = compactPointViewScale
                        } else if (shouldUseUltraCompactStyle) {
                            cardView.visibility = View.INVISIBLE
                            pointView.scaleX = ultraCompactPointViewScale
                            pointView.scaleY = ultraCompactPointViewScale
                        } else {
                            cardView.visibility = View.VISIBLE
                            pointView.scaleX = 1.0f
                            pointView.scaleY = 1.0f
                        }
                    }
                }
            }
        })
    }

    private fun showLocationPuck() {
        mapView.location.locationPuck = LocationPuck2D(
            topImage = ImageHolder.Companion.from(com.mapbox.maps.plugin.locationcomponent.R.drawable.mapbox_user_icon),
            bearingImage = ImageHolder.Companion.from(com.mapbox.maps.plugin.locationcomponent.R.drawable.mapbox_user_bearing_icon),
            shadowImage = null
        )
        mapView.location.enabled = true
        mapView.location.puckBearing = PuckBearing.HEADING
        mapView.viewport.transitionTo(
            targetState = mapView.viewport.makeFollowPuckViewportState(),
            transition = mapView.viewport.makeImmediateViewportTransition()
        )
    }

    private fun requestLocationPermission() {
        val permissionsManager = PermissionsManager(object : PermissionsListener {
            override fun onExplanationNeeded(permissionsToExplain: List<String>) {}
            override fun onPermissionResult(granted: Boolean) {
                if (granted) {
                    restartApp()
                } else {
                    checkForUpdate()
                }
            }
        })

        if (PermissionsManager.Companion.areLocationPermissionsGranted(this)) {
            checkForUpdate()
        } else {
            permissionsManager.requestLocationPermissions(this)
        }
    }

    private fun checkForUpdate() {
        AppUpdateChecker.Companion.checkForUpdate(this) { isUpdateAvailable ->
            if (isUpdateAvailable) {
                startActivity(
                    Intent(this, AppUpdateActivity::class.java),
                    ActivityOptions.makeCustomAnimation(
                        this,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                    ).toBundle()
                )
            }
        }
    }

    private fun restartApp() {
        val intent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val mainIntent = Intent.makeRestartActivityTask(intent?.component)
        applicationContext.startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }

        loadMapStyle(newConfig.isNightModeActive)
    }
}