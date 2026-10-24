package com.hapticsound.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.hapticsound.app.audio.SoundEngine
import com.hapticsound.app.data.HapticPreferences
import com.hapticsound.app.sensor.VibrationSensorEngine

class HapticAccessibilityService : AccessibilityService() {

    private lateinit var prefs: HapticPreferences

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = HapticPreferences(applicationContext)
        VibrationSensorEngine.start(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!::prefs.isInitialized) {
            prefs = HapticPreferences(applicationContext)
        }
        if (!prefs.isMasterEnabled) return

        // Ensure sensor engine is always alive
        VibrationSensorEngine.start(applicationContext)

        if (prefs.enableUiClicks &&
            (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
             event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED)
        ) {
            SoundEngine.playSound(
                preset = prefs.selectedPreset,
                volume = prefs.volume,
                pitch = prefs.pitch,
                repeatCount = 1,
                useAlarmStream = prefs.useAlarmChannel
            )
        }
    }

    override fun onInterrupt() {}
}
