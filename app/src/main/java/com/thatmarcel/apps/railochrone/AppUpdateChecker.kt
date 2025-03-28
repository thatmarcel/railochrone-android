package com.thatmarcel.apps.railochrone

import android.content.Context
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class AppUpdateChecker {
    companion object {
        private var hasCheckedBefore = false

        var apkDownloadUrl: String? = null

        fun checkForUpdate(context: Context, completion: (isNewUpdateAvailable: Boolean) -> Unit) {
            if (hasCheckedBefore) {
                completion(false)
                return
            }

            hasCheckedBefore = true

            val request = Request.Builder()
                .url("https://api.github.com/repos/thatmarcel/railochrone-android/releases/latest")
                .build()

            OkHttpClient().newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body ?: return

                    val responseString = responseBody.string()

                    val releaseInfo: GithubReleaseInfo =
                        Gson().fromJson(responseString, GithubReleaseInfo::class.java)

                    val releaseVersionName = releaseInfo.tagName

                    val currentlyInstalledVersionName = context.packageManager
                        .getPackageInfo(context.packageName, 0)
                        .versionName ?: return

                    apkDownloadUrl = releaseInfo.assets.first { it.name.endsWith(".apk") }.downloadUrl

                    completion(releaseVersionName != currentlyInstalledVersionName)
                }
            })
        }
    }
}