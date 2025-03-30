package com.thatmarcel.apps.railochrone.helpers.types

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class DistanceCalculator {
    companion object {
        private const val EARTH_RADIUS = 6371

        private fun haversine(value: Double): Double {
            return sin(value / 2).pow(2.0)
        }

        fun calculateDistanceBetweenCoordinates(
            startLat: Double,
            startLong: Double,
            endLat: Double,
            endLong: Double
        ): Double {
            var startLat = startLat
            var endLat = endLat
            val dLat = Math.toRadians((endLat - startLat))
            val dLong = Math.toRadians((endLong - startLong))

            startLat = Math.toRadians(startLat)
            endLat = Math.toRadians(endLat)

            val a = haversine(dLat) + cos(startLat) * cos(endLat) * haversine(dLong)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))

            return EARTH_RADIUS * c
        }
    }
}