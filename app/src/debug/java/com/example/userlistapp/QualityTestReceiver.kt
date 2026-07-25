package com.example.userlistapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.userlistapp.core.quality.AppQualityMonitor
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class QualityTestReceiver : BroadcastReceiver() {
    @Inject
    lateinit var qualityMonitor: AppQualityMonitor

    override fun onReceive(context: Context, intent: Intent) {
        if (!qualityMonitor.isEnabled) {
            Log.w(LOG_TAG, FIREBASE_UNAVAILABLE_MESSAGE)
            return
        }
        when (intent.action) {
            ACTION_NON_FATAL -> {
                qualityMonitor.setCustomKey(QUALITY_TEST_SOURCE_KEY, ADB_SOURCE_VALUE)
                qualityMonitor.log(NON_FATAL_REQUESTED_MESSAGE)
                qualityMonitor.recordNonFatal(
                    IllegalStateException(NON_FATAL_ERROR_MESSAGE),
                )
                Log.i(LOG_TAG, NON_FATAL_RECORDED_MESSAGE)
            }

            ACTION_CRASH -> throw IllegalStateException(CRASH_ERROR_MESSAGE)
        }
    }

    private companion object {
        const val LOG_TAG = "QualityTest"
        const val ACTION_NON_FATAL = "com.example.userlistapp.QUALITY_NON_FATAL"
        const val ACTION_CRASH = "com.example.userlistapp.QUALITY_CRASH"
        private const val FIREBASE_UNAVAILABLE_MESSAGE =
            "Firebase is unavailable; check app/google-services.json"
        private const val QUALITY_TEST_SOURCE_KEY = "quality_test_source"
        private const val ADB_SOURCE_VALUE = "adb"
        private const val NON_FATAL_REQUESTED_MESSAGE = "Deliberate debug non-fatal requested"
        private const val NON_FATAL_ERROR_MESSAGE = "Deliberate debug non-fatal"
        private const val NON_FATAL_RECORDED_MESSAGE = "Crashlytics non-fatal recorded"
        private const val CRASH_ERROR_MESSAGE = "Deliberate debug Crashlytics crash"
    }
}
