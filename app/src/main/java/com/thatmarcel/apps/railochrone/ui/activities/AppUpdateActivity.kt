package com.thatmarcel.apps.railochrone.ui.activities

import android.R
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsetsController
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.thatmarcel.apps.railochrone.helpers.AppUpdateChecker
import com.thatmarcel.apps.railochrone.helpers.ProgressResponseBody
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException

class AppUpdateActivity: AppCompatActivity() {
    private lateinit var downloadAndInstallButton: Button
    private lateinit var dismissButton: Button

    private lateinit var apkDownloadUrl: String

    private lateinit var progressIndicator: LinearProgressIndicator

    private var currentDownloadProgressFraction = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        if (AppUpdateChecker.Companion.apkDownloadUrl == null) {
            finishAndOverrideTransitionIfNeeded()
            return
        }

        apkDownloadUrl = AppUpdateChecker.Companion.apkDownloadUrl!!

        setupView()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.fade_in, R.anim.fade_out)
        }
    }

    private fun setupView() {
        setContentView(com.thatmarcel.apps.railochrone.R.layout.activity_app_update)

        downloadAndInstallButton = findViewById(com.thatmarcel.apps.railochrone.R.id.content_app_update_download_and_install_button)
        dismissButton = findViewById(com.thatmarcel.apps.railochrone.R.id.content_app_update_dismiss_button)

        progressIndicator = findViewById(com.thatmarcel.apps.railochrone.R.id.content_app_update_progress_indicator)

        dismissButton.setOnClickListener {
            val permissionsManager = PermissionsManager(object : PermissionsListener {
                override fun onExplanationNeeded(permissionsToExplain: List<String>) {}
                override fun onPermissionResult(granted: Boolean) {}
            })

            if (PermissionsManager.Companion.areLocationPermissionsGranted(this)) {
                finishAndOverrideTransitionIfNeeded()
            } else {
                permissionsManager.requestLocationPermissions(this)

                Handler(Looper.getMainLooper()).postDelayed({
                    finishAndOverrideTransitionIfNeeded()
                }, 500)
            }
        }

        downloadAndInstallButton.setOnClickListener {
            handleDownloadAndInstallButtonClick()
        }
    }

    private fun handleDownloadAndInstallButtonClick() {
        updateViewsForDownloading()

        startDownloading()
    }

    private fun updateViewsForDownloading() {
        dismissButton.visibility = View.GONE
        downloadAndInstallButton.visibility = View.GONE

        progressIndicator.isIndeterminate = false
        progressIndicator.min = 0
        progressIndicator.max = 100
        progressIndicator.progress = (currentDownloadProgressFraction * 100.0).toInt()
        progressIndicator.showAnimationBehavior = LinearProgressIndicator.SHOW_NONE

        progressIndicator.visibility = View.VISIBLE
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun startDownloading() {
        val outputFile = File.createTempFile("railochrone-latest-", ".apk", cacheDir)

        val progressListener = object : ProgressResponseBody.ProgressListener {
            override fun update(progressFraction: Double, hasFinished: Boolean) {
                runOnUiThread {
                    currentDownloadProgressFraction = progressFraction

                    progressIndicator.progress = (progressFraction * 100.0).toInt()
                }
            }
        }

        val client = OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                val originalResponse = chain.proceed(chain.request())
                return@addNetworkInterceptor originalResponse.newBuilder()
                    .body(ProgressResponseBody(originalResponse.body, progressListener))
                    .build()
            }
            .build()

        val request = Request.Builder()
            .url(apkDownloadUrl)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                finishAndOverrideTransitionIfNeeded()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body ?: return

                @Suppress("DeferredResultUnused")
                GlobalScope.async {
                    val bufferedSink = outputFile.sink().buffer()
                    bufferedSink.writeAll(responseBody.source())
                    bufferedSink.close()

                    runOnUiThread {
                        installUpdateFromFile(outputFile)
                    }
                }
            }
        })
    }

    @SuppressLint("SetWorldReadable")
    private fun installUpdateFromFile(downloadedFile: File) {
        downloadedFile.setReadable(true, false)

        val downloadedFileURI = FileProvider.getUriForFile(
            this,
            "com.thatmarcel.apps.railochrone.fileprovider",
            downloadedFile
        )

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(downloadedFileURI, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        startActivity(intent)

        finishAndOverrideTransitionIfNeeded()
    }

    private fun finishAndOverrideTransitionIfNeeded() {
        finishAfterTransition()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }

        enableEdgeToEdge()

        setupView()

        if (currentDownloadProgressFraction > 0.0) {
            updateViewsForDownloading()
        }

        if (!newConfig.isNightModeActive) {
            window.decorView.windowInsetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            window.decorView.windowInsetsController?.setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
    }
}