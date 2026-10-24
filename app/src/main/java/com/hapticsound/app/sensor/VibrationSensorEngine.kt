package com.hapticsound.app.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.hapticsound.app.audio.SoundEngine
import com.hapticsound.app.data.DetectionMode
import com.hapticsound.app.data.HapticPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Detects physical vibration motor activation (from games or any app) using
 * high-sampling-rate Accelerometer + Gyroscope filtering.
 *
 * Physics principle:
 * - Hand movement / tilting the phone happens at low frequencies (< 8 Hz), where
 *   consecutive acceleration deltas point in the SAME direction and angular velocity (gyro) is high.
 * - The phone's internal vibration motor (LRA / ERM) oscillates at 130..240 Hz, causing
 *   rapid sign reversals in consecutive samples (2nd derivative / high-pass energy)
 *   with minimal macro rotation!
 */
object VibrationSensorEngine : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var prefs: HapticPreferences? = null
    private var isRunning = false

    // Live telemetry for UI calibration oscilloscope
    private val _liveVibrationLevel = MutableStateFlow(0f)
    val liveVibrationLevel: StateFlow<Float> = _liveVibrationLevel

    private val _triggerCount = MutableStateFlow(0)
    val triggerCount: StateFlow<Int> = _triggerCount

    private var prevX = 0f
    private var prevY = 0f
    private var prevZ = 0f
    private var prevDx = 0f
    private var prevDy = 0f
    private var prevDz = 0f
    private var hasPrev = false

    private var currentGyroMagnitude = 0f
    private var highFreqEnergyEma = 0f
    private var consecutiveMotorSamples = 0
    private var lastTriggerTimestamp = 0L

    fun start(context: Context) {
        if (isRunning) return
        val appContext = context.applicationContext
        prefs = HapticPreferences(appContext)
        val sm = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sensorManager = sm

        val accelSensor = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroSensor = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        if (accelSensor != null) {
            try {
                // Request fastest sampling rate (~200-400Hz) unlocked by HIGH_SAMPLING_RATE_SENSORS
                sm.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_FASTEST)
            } catch (_: Exception) {
                sm.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }

        if (gyroSensor != null) {
            sm.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
        }

        hasPrev = false
        highFreqEnergyEma = 0f
        consecutiveMotorSamples = 0
        isRunning = true
    }

    fun stop() {
        if (!isRunning) return
        sensorManager?.unregisterListener(this)
        isRunning = false
        _liveVibrationLevel.value = 0f
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val currentPrefs = prefs ?: return

        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            val gx = event.values[0]
            val gy = event.values[1]
            val gz = event.values[2]
            currentGyroMagnitude = sqrt(gx * gx + gy * gy + gz * gz)
            return
        }

        if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION ||
            event.sensor.type == Sensor.TYPE_ACCELEROMETER
        ) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            if (!hasPrev) {
                prevX = x
                prevY = y
                prevZ = z
                hasPrev = true
                return
            }

            // 1st derivative (jerk step)
            val dx = x - prevX
            val dy = y - prevY
            val dz = z - prevZ

            // Dot product between current step and previous step:
            // Negative dot product means the acceleration vector just reversed direction (oscillation!)
            val dot = dx * prevDx + dy * prevDy + dz * prevDz
            val stepMag = sqrt(dx * dx + dy * dy + dz * dz)

            // 2nd derivative magnitude (high-pass filter isolating >50Hz vibration motor energy)
            val ddx = dx - prevDx
            val ddy = dy - prevDy
            val ddz = dz - prevDz
            val secondDiffMag = sqrt(ddx * ddx + ddy * ddy + ddz * ddz) * 0.5f

            prevX = x
            prevY = y
            prevZ = z
            prevDx = dx
            prevDy = dy
            prevDz = dz

            val mode = currentPrefs.detectionMode
            val rawMotorSignal = if (mode == DetectionMode.MOTOR_IN_HANDS) {
                // Penalize macro hand rotation (gyro > 1.2 rad/s) and reward direction-reversing oscillation
                val gyroPenalty = if (currentGyroMagnitude > 1.2f) 0.2f else 1.0f
                val reversalBoost = if (dot < 0f) 1.15f else 0.45f
                secondDiffMag * gyroPenalty * reversalBoost
            } else {
                // ON_TABLE_ULTRA: maximum sensitivity to any chassis micro-vibration
                stepMag
            }

            // Fast attack, smooth decay envelope follower
            highFreqEnergyEma = if (rawMotorSignal > highFreqEnergyEma) {
                highFreqEnergyEma * 0.45f + rawMotorSignal * 0.55f
            } else {
                highFreqEnergyEma * 0.85f + rawMotorSignal * 0.15f
            }

            _liveVibrationLevel.value = highFreqEnergyEma

            if (!currentPrefs.isMasterEnabled || !currentPrefs.enableGameVibrationDetector) {
                return
            }

            val now = System.currentTimeMillis()
            // Ignore speaker resonance while our own sound is actively playing
            if (now < SoundEngine.isPlayingSelfSoundUntil) {
                consecutiveMotorSamples = 0
                return
            }

            val threshold = currentPrefs.vibrationThreshold
            val requiredSamples = if (mode == DetectionMode.MOTOR_IN_HANDS) 3 else 2

            if (highFreqEnergyEma >= threshold) {
                consecutiveMotorSamples++
                if (consecutiveMotorSamples >= requiredSamples) {
                    val cooldown = currentPrefs.cooldownMs
                    if (now - lastTriggerTimestamp >= cooldown) {
                        lastTriggerTimestamp = now
                        consecutiveMotorSamples = 0
                        _triggerCount.value += 1

                        SoundEngine.playSound(
                            preset = currentPrefs.selectedPreset,
                            volume = currentPrefs.volume,
                            pitch = currentPrefs.pitch,
                            repeatCount = currentPrefs.repeatCount.count,
                            useAlarmStream = currentPrefs.useAlarmChannel
                        )
                    }
                }
            } else {
                if (consecutiveMotorSamples > 0) consecutiveMotorSamples--
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
