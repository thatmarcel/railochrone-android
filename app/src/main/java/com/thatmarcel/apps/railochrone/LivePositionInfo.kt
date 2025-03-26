package com.thatmarcel.apps.railochrone

import com.google.gson.annotations.SerializedName

data class LivePositionInfo(
    val id: String,
    val journeyIdentifier: String,
    val delay: Double,
    val delayInSeconds: Double,
    val direction: String,
    val line: String,
    val latitude: Double,
    val longitude: Double,
    val type: String,
    @SerializedName("ModCode")
    val modCode: Double,
    @SerializedName("MOTCode")
    val motCode: Double,
    val realtime: Double,
    val timestamp: String,
    val previous: LivePositionInfoPreviousInfo
)

data class LivePositionInfoPreviousInfo(
    val latitude: Double,
    val longitude: Double
)
