package com.example

import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager

/**
 * Keeps the display awake while NFL LIVE is foregrounded on Android TV.
 * Phone/tablet behavior is intentionally unchanged.
 */
class NflApplication : Application() {

    private val isTelevisionDevice: Boolean
        get() = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (isTelevisionDevice && activity is MainActivity) {
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (activity is MainActivity) {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}