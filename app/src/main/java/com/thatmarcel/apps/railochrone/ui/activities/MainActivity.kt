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
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.asynclayoutinflater.view.AsyncLayoutInflater
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.android.gestures.RotateGestureDetector
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
import com.mapbox.maps.plugin.annotation.AnnotationConfig
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotation
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.OnRotateListener
import com.mapbox.maps.plugin.gestures.OnScaleListener
import com.mapbox.maps.plugin.gestures.addOnMoveListener
import com.mapbox.maps.plugin.gestures.addOnRotateListener
import com.mapbox.maps.plugin.gestures.addOnScaleListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.maps.plugin.viewport.viewport
import com.mapbox.maps.viewannotation.ViewAnnotationUpdateMode
import com.mapbox.maps.viewannotation.geometry
import com.thatmarcel.apps.railochrone.R
import com.thatmarcel.apps.railochrone.helpers.AppUpdateChecker
import com.thatmarcel.apps.railochrone.helpers.types.DistanceCalculator
import com.thatmarcel.apps.railochrone.helpers.types.LivePositionDisplayInfo
import com.thatmarcel.apps.railochrone.helpers.types.LivePositionInfo
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
    private lateinit var centerCurrentLocationFloatingActionButton: FloatingActionButton

    private lateinit var circleAnnotationManager: CircleAnnotationManager

    val okHttpClient = OkHttpClient()

    val livePositionDisplayInfos: MutableList<LivePositionDisplayInfo> = mutableListOf()

    val compactPointStyleAbsoluteZoomThreshold = 7.5
    val compactPointStyleConditionalZoomThreshold = 12.75
    val compactPointStyleConditionalPointCountThreshold = 25

    val maxNumberOfLivePositionPoints = 75

    val viewportCoordinatePadding = 0.25

    private lateinit var asyncLayoutInflater: AsyncLayoutInflater

    private lateinit var cachedLivePositionAnnotationViews: MutableList<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        setupViews()

        requestLocationPermission()
        showLocationPuck()

        loadMapStyle(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && resources.configuration.isNightModeActive)

        updateRealtimeDataAfterDelay()

        addMapListeners()
    }

    @SuppressLint("RtlHardcoded")
    private fun setupViews() {
        mapView = findViewById(R.id.activity_main_map_view)
        centerCurrentLocationFloatingActionButton = findViewById(R.id.activity_main_center_current_location_floating_action_button)

        mapView.scalebar.enabled = false
        mapView.compass.enabled = false

        mapView.logo.position = Gravity.BOTTOM or Gravity.LEFT
        mapView.attribution.position = Gravity.BOTTOM or Gravity.LEFT

        centerCurrentLocationFloatingActionButton.setOnClickListener {
            mapView.viewport.transitionTo(
                targetState = mapView.viewport.makeFollowPuckViewportState(),
                transition = mapView.viewport.makeImmediateViewportTransition()
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            mapView.logo.marginBottom += systemBars.bottom + 2
            mapView.logo.marginLeft += 24
            mapView.attribution.marginBottom += systemBars.bottom + 2
            mapView.attribution.marginLeft += 24

            val centerLocationButtonLayoutParams = centerCurrentLocationFloatingActionButton.layoutParams as ViewGroup.MarginLayoutParams
            centerLocationButtonLayoutParams.setMargins(
                centerLocationButtonLayoutParams.leftMargin + systemBars.left,
                centerLocationButtonLayoutParams.topMargin,
                centerLocationButtonLayoutParams.rightMargin + systemBars.right,
                systemBars.bottom
            )
            centerCurrentLocationFloatingActionButton.layoutParams = centerLocationButtonLayoutParams

            insets
        }

        circleAnnotationManager = mapView.annotations.createCircleAnnotationManager(AnnotationConfig())

        mapView.viewAnnotationManager.setViewAnnotationUpdateMode(
            ViewAnnotationUpdateMode.MAP_FIXED_DELAY
        )

        asyncLayoutInflater = AsyncLayoutInflater(this)

        val livePositionAnnotationViewsToBeCached: MutableList<View> = mutableListOf()
        repeat(maxNumberOfLivePositionPoints) {
            mapView.viewAnnotationManager.addViewAnnotation(
                R.layout.live_position_marker,
                ViewAnnotationOptions.Builder()
                    .visible(false)
                    .geometry(
                        Point.fromLngLat(9.177, 48.773)
                    )
                    .allowOverlap(true)
                    .allowOverlapWithPuck(true)
                    .build(),
                asyncLayoutInflater
            ) { annotationView ->
                livePositionAnnotationViewsToBeCached.add(annotationView)

                if (livePositionAnnotationViewsToBeCached.size == maxNumberOfLivePositionPoints) {
                    cachedLivePositionAnnotationViews = livePositionAnnotationViewsToBeCached
                }
            }
        }
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

    private fun addMapListeners() {
        mapView.mapboxMap.addOnScaleListener(object : OnScaleListener {
            override fun onScale(detector: StandardScaleGestureDetector) {
                updatePointAnnotations()
            }

            override fun onScaleBegin(detector: StandardScaleGestureDetector) { }
            override fun onScaleEnd(detector: StandardScaleGestureDetector) { }
        })

        mapView.mapboxMap.addOnMoveListener(object : OnMoveListener {
            override fun onMove(detector: MoveGestureDetector): Boolean {
                updatePointAnnotations()

                return false
            }

            override fun onMoveBegin(detector: MoveGestureDetector) { }
            override fun onMoveEnd(detector: MoveGestureDetector) { }
        })

        mapView.mapboxMap.addOnRotateListener(object : OnRotateListener {
            override fun onRotate(detector: RotateGestureDetector) {
                updatePointAnnotations()
            }

            override fun onRotateBegin(detector: RotateGestureDetector) { }
            override fun onRotateEnd(detector: RotateGestureDetector) { }
        })
    }

    private fun updatePointAnnotations() {
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
        )
        val lonMin = min(
            coordinateBounds.northeast.longitude(),
            coordinateBounds.southwest.longitude()
        )
        val latMax = max(
            coordinateBounds.northeast.latitude(),
            coordinateBounds.southwest.latitude()
        )
        val lonMax = max(
            coordinateBounds.northeast.longitude(),
            coordinateBounds.southwest.longitude()
        )

        val shouldUseCompactPointStyle = (
            mapView.mapboxMap.cameraState.zoom < compactPointStyleAbsoluteZoomThreshold ||
            (
                mapView.mapboxMap.cameraState.zoom < compactPointStyleConditionalZoomThreshold &&
                livePositionDisplayInfos
                    .filter { it.positionInfo.latitude in latMin..latMax && it.positionInfo.longitude in lonMin..lonMax }
                    .size > compactPointStyleConditionalPointCountThreshold
            )
        )

        val isChangingPointAnnotationType = livePositionDisplayInfos
            .firstOrNull()
            ?.isCompact() == !shouldUseCompactPointStyle

        if (isChangingPointAnnotationType) {
            if (shouldUseCompactPointStyle) {
                circleAnnotationManager.create(
                    livePositionDisplayInfos
                        .map { createAnnotationCircleOptions(it.positionInfo) }
                ).forEachIndexed { circleAnnotationIndex, circleAnnotation ->
                    val livePositionDisplayInfo = livePositionDisplayInfos[circleAnnotationIndex]

                    hideAnnotationViewForDisplayInfo(livePositionDisplayInfo)
                    livePositionDisplayInfo.circleAnnotation = circleAnnotation
                }
            } else {
                circleAnnotationManager.deleteAll()
                livePositionDisplayInfos.forEachIndexed { i, it ->
                    it.circleAnnotation = null
                    showAnnotationViewForDisplayInfo(it)
                }
            }
        }
    }

    private fun updateRealtimeDataAfterDelay() {
        Handler(Looper.getMainLooper()).postDelayed({
            updateRealtimeData()

            if (!isDestroyed) {
                updateRealtimeDataAfterDelay()
            }
        }, 2500)
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
        )
        val lonMin = min(
            coordinateBounds.northeast.longitude(),
            coordinateBounds.southwest.longitude()
        )
        val latMax = max(
            coordinateBounds.northeast.latitude(),
            coordinateBounds.southwest.latitude()
        )
        val lonMax = max(
            coordinateBounds.northeast.longitude(),
            coordinateBounds.southwest.longitude()
        )

        val latCenter = mapView.mapboxMap.cameraState.center.latitude()
        val lonCenter = mapView.mapboxMap.cameraState.center.longitude()

        val url = "https://livekarte.vvs.de/proxy/livepositions?latMin=${
            latMin - viewportCoordinatePadding
        }&lonMin=${
            lonMin - viewportCoordinatePadding
        }&latMax=${
            latMax + viewportCoordinatePadding
        }&lonMax=${
            lonMax + viewportCoordinatePadding
        }"

        val request = Request.Builder()
            .url(url)
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                val responseBodyString = response.body?.string() ?: return

                var newLivePositionInfos: List<LivePositionInfo> = Gson().fromJson(
                    responseBodyString,
                    object : TypeToken<List<LivePositionInfo>>() {}.type
                )

                newLivePositionInfos = newLivePositionInfos.sortedBy {
                    DistanceCalculator.calculateDistanceBetweenCoordinates(
                        latCenter,
                        lonCenter,
                        it.latitude,
                        it.longitude
                    )
                }

                Handler(Looper.getMainLooper()).post {
                    val shouldUseCompactPointStyle = (
                        mapView.mapboxMap.cameraState.zoom < compactPointStyleAbsoluteZoomThreshold ||
                        (
                            mapView.mapboxMap.cameraState.zoom < compactPointStyleConditionalZoomThreshold &&
                            newLivePositionInfos
                                .filter { it.latitude in latMin..latMax && it.longitude in lonMin..lonMax }
                                .size > compactPointStyleConditionalPointCountThreshold
                        )
                    )

                    val isChangingPointAnnotationType = livePositionDisplayInfos
                        .firstOrNull()
                        ?.isCompact() == !shouldUseCompactPointStyle

                    livePositionDisplayInfos
                        .toList()
                        .forEach { a ->
                            if (
                                newLivePositionInfos.none { b -> a.positionInfo.id == b.id } ||
                                (
                                    !shouldUseCompactPointStyle &&
                                    newLivePositionInfos.indexOfFirst { b ->
                                        b.id == a.positionInfo.id
                                    } >= maxNumberOfLivePositionPoints
                                )
                            ) {
                                if (a.isCompact()) {
                                    circleAnnotationManager.delete(a.circleAnnotation!!)
                                    a.circleAnnotation = null
                                } else {
                                    hideAnnotationViewForDisplayInfo(a)
                                }

                                livePositionDisplayInfos.remove(a)
                            }
                        }

                    val annotationCirclesToUpdate: MutableList<CircleAnnotation> = mutableListOf()
                    val annotationCirclesToRemove: MutableList<CircleAnnotation> = mutableListOf()

                    newLivePositionInfos.forEachIndexed { newLivePositionInfoIndex, newLivePositionInfo ->
                        val prevPositionDisplayInfo = livePositionDisplayInfos
                            .firstOrNull { it.positionInfo.id == newLivePositionInfo.id }

                        if (prevPositionDisplayInfo == null) {
                            val newPositionDisplayInfo = LivePositionDisplayInfo(
                                newLivePositionInfo,
                                null,
                                null,
                                null
                            )

                            if (shouldUseCompactPointStyle) {
                                newPositionDisplayInfo.circleAnnotationOptions = createAnnotationCircleOptions(newLivePositionInfo)
                            } else {
                                showAnnotationViewForDisplayInfo(newPositionDisplayInfo)
                            }

                            livePositionDisplayInfos.add(newPositionDisplayInfo)
                        } else {
                            if (isChangingPointAnnotationType) {
                                prevPositionDisplayInfo.positionInfo = newLivePositionInfo

                                if (shouldUseCompactPointStyle) {
                                    hideAnnotationViewForDisplayInfo(prevPositionDisplayInfo)

                                    prevPositionDisplayInfo.circleAnnotationOptions = createAnnotationCircleOptions(newLivePositionInfo)
                                } else {
                                    annotationCirclesToRemove.add(prevPositionDisplayInfo.circleAnnotation!!)
                                    prevPositionDisplayInfo.circleAnnotation = null
                                    showAnnotationViewForDisplayInfo(prevPositionDisplayInfo)
                                }
                            } else {
                                if (
                                    prevPositionDisplayInfo.positionInfo.longitude != newLivePositionInfo.longitude ||
                                    prevPositionDisplayInfo.positionInfo.latitude != newLivePositionInfo.latitude
                                ) {
                                    prevPositionDisplayInfo.positionInfo = newLivePositionInfo

                                    if (shouldUseCompactPointStyle) {
                                        val annotationCircle = prevPositionDisplayInfo.circleAnnotation!!

                                        annotationCircle.point = Point.fromLngLat(
                                            newLivePositionInfo.longitude,
                                            newLivePositionInfo.latitude
                                        )

                                        annotationCirclesToUpdate.add(annotationCircle)
                                    } else {
                                        val annotationView = prevPositionDisplayInfo.annotationView

                                        if (annotationView != null) {
                                            mapView.viewAnnotationManager.updateViewAnnotation(
                                                annotationView,
                                                ViewAnnotationOptions.Builder()
                                                    .geometry(
                                                        Point.fromLngLat(
                                                            newLivePositionInfo.longitude,
                                                            newLivePositionInfo.latitude
                                                        )
                                                    )
                                                    .build()
                                            )
                                        } else {
                                            showAnnotationViewForDisplayInfo(prevPositionDisplayInfo)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val livePositionDisplayInfosWithCircleAnnotationsToAdd = livePositionDisplayInfos
                        .filter { it.circleAnnotationOptions != null }

                    circleAnnotationManager.create(
                        livePositionDisplayInfosWithCircleAnnotationsToAdd
                            .map { it.circleAnnotationOptions!! }
                    ).forEachIndexed { circleAnnotationIndex, circleAnnotation ->
                        val livePositionDisplayInfo = livePositionDisplayInfosWithCircleAnnotationsToAdd[circleAnnotationIndex]

                        livePositionDisplayInfo.circleAnnotationOptions = null
                        livePositionDisplayInfo.circleAnnotation = circleAnnotation
                    }

                    circleAnnotationManager.update(annotationCirclesToUpdate)
                    circleAnnotationManager.delete(annotationCirclesToRemove)

                    livePositionDisplayInfos.sortBy {
                        DistanceCalculator.calculateDistanceBetweenCoordinates(
                            latCenter,
                            lonCenter,
                            it.positionInfo.latitude,
                            it.positionInfo.longitude
                        )
                    }
                }
            }
        })
    }

    @ColorInt
    private fun getPointColorForLivePositionInfo(livePositionInfo: LivePositionInfo): Int {
        return if (livePositionInfo.type == "Bus") {
            Color.rgb(179, 46, 45)
        } else if (livePositionInfo.type == "Stadtbahn") {
            Color.rgb(65, 140, 195)
        } else {
            Color.rgb(108, 177, 70)
        }
    }

    private fun showAnnotationViewForDisplayInfo(livePositionDisplayInfo: LivePositionDisplayInfo) {
        if (cachedLivePositionAnnotationViews.isEmpty()) {
            return
        }

        @ColorInt val pointColor = getPointColorForLivePositionInfo(livePositionDisplayInfo.positionInfo)

        val annotationView = cachedLivePositionAnnotationViews.removeAt(0)

        val lineNameTextView: TextView = annotationView.findViewById(R.id.live_position_marker_line_name_text_view)
        val directionTextView: TextView = annotationView.findViewById(R.id.live_position_marker_direction_text_view)
        val pointView: ImageView = annotationView.findViewById(R.id.live_position_marker_point_view)
        val cardView: MaterialCardView = annotationView.findViewById(R.id.live_position_marker_card_view)

        lineNameTextView.text = livePositionDisplayInfo.positionInfo.line
            .replace("S-Bahn ", "")
            .replace("R-Bahn ", "")
            .replace("Stadtbahn ", "")
            .replace("Bus ", "")

        directionTextView.text = livePositionDisplayInfo.positionInfo.direction

        pointView.setColorFilter(pointColor)
        cardView.setCardBackgroundColor(pointColor)

        mapView.viewAnnotationManager.updateViewAnnotation(
            annotationView,
            ViewAnnotationOptions.Builder()
                .geometry(
                    Point.fromLngLat(livePositionDisplayInfo.positionInfo.longitude, livePositionDisplayInfo.positionInfo.latitude)
                )
                .visible(true)
                .build()
        )

        livePositionDisplayInfo.annotationView = annotationView

        return
    }

    private fun hideAnnotationViewForDisplayInfo(livePositionDisplayInfo: LivePositionDisplayInfo) {
        if (livePositionDisplayInfo.annotationView == null) {
            return
        }

        cachedLivePositionAnnotationViews.add(livePositionDisplayInfo.annotationView!!)

        mapView.viewAnnotationManager.updateViewAnnotation(
            livePositionDisplayInfo.annotationView!!,
            ViewAnnotationOptions.Builder()
                .visible(false)
                .build()
        )

        livePositionDisplayInfo.annotationView = null
    }

    private fun createAnnotationCircleOptions(livePositionInfo: LivePositionInfo): CircleAnnotationOptions {
        @ColorInt val pointColor = getPointColorForLivePositionInfo(livePositionInfo)

        val circleAnnotationOptions = CircleAnnotationOptions()
            .withPoint(
                Point.fromLngLat(
                    livePositionInfo.longitude,
                    livePositionInfo.latitude
                )
            )
            .withCircleColor(pointColor)
            .withCircleRadius(5.0)

        return circleAnnotationOptions
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super<AppCompatActivity>.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val hasGrantedPermission = grantResults.any { it == 0 }

        if (hasGrantedPermission) {
            centerCurrentLocationFloatingActionButton.visibility = View.VISIBLE
        }

        checkForUpdate()
    }

    private fun requestLocationPermission() {
        val permissionsManager = PermissionsManager(object : PermissionsListener {
            override fun onExplanationNeeded(permissionsToExplain: List<String>) { }
            override fun onPermissionResult(granted: Boolean) { }
        })

        if (PermissionsManager.Companion.areLocationPermissionsGranted(this)) {
            centerCurrentLocationFloatingActionButton.visibility = View.VISIBLE

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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }

        loadMapStyle(newConfig.isNightModeActive)
    }
}