package com.thatmarcel.apps.railochrone

import com.google.gson.annotations.SerializedName

data class GithubReleaseInfo(
    @SerializedName("tag_name")
    val tagName: String,
    val assets: List<GithubReleaseInfoAssetInfo>
)

data class GithubReleaseInfoAssetInfo(
    @SerializedName("browser_download_url")
    val downloadUrl: String,
    val name: String
)