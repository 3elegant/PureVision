package com.deenelife.purevison

import android.app.Application
import com.google.android.material.color.DynamicColors

class PureVisionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply Material You dynamic color theme to all activities if available (Android 12+)
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
