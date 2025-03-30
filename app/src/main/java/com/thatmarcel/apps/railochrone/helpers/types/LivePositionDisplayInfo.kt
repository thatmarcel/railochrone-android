package com.thatmarcel.apps.railochrone.helpers.types

import android.view.View
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotation
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions

data class LivePositionDisplayInfo(
    var positionInfo: LivePositionInfo,
    var annotationView: View?,
    var circleAnnotationOptions: CircleAnnotationOptions?,
    var circleAnnotation: CircleAnnotation?
) {
    fun isCompact(): Boolean = circleAnnotation != null

    fun describe(): String {
        return "LivePositionDisplayInfo (latitude: ${
            positionInfo.latitude
        }, longitude: ${
            positionInfo.longitude
        }, annotation view: $annotationView, circle annotation options: $circleAnnotationOptions, circle annotation: $circleAnnotation)"
    }
}