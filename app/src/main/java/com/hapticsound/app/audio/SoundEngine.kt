package com.hapticsound.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.hapticsound.app.data.SoundPreset
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

object SoundEngine {
    private const val SAMPLE_RATE = 44100
    private val executor = Executors.newSingleThreadExecutor()

    // Timestamp until which speaker acoustic resonance is ignored by the sensor
    @Volatile
    var isPlayingSelfSoundUntil: Long = 0L
        private set

    fun playSound(
        preset: SoundPreset,
        volume: Float,
        pitch: Float = 1.0f,
        repeatCount: Int = 1,
        useAlarmStream: Boolean = false
    ) {
        val effectiveVolume = volume.coerceIn(0.05f, 1.0f)
        val repeats = repeatCount.coerceIn(1, 3)

        executor.execute {
            try {
                val singlePcm = synthesizeWaveform(preset, effectiveVolume, pitch)
                val gapSamples = (SAMPLE_RATE * 0.08).toInt() // 80ms gap between repeats
                val totalSamples = if (repeats == 1) {
                    singlePcm.size
                } else {
                    singlePcm.size * repeats + gapSamples * (repeats - 1)
                }

                val fullPcm = ShortArray(totalSamples)
                var offset = 0
                for (r in 0 until repeats) {
                    System.arraycopy(singlePcm, 0, fullPcm, offset, singlePcm.size)
                    offset += singlePcm.size
                    if (r < repeats - 1) {
                        offset += gapSamples
                    }
                }

                val durationMs = (totalSamples * 1000L) / SAMPLE_RATE
                isPlayingSelfSoundUntil = System.currentTimeMillis() + durationMs + 180L

                val usage = if (useAlarmStream) {
                    AudioAttributes.USAGE_ALARM
                } else {
                    AudioAttributes.USAGE_MEDIA
                }

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferBytes = (fullPcm.size * 2).coerceAtLeast(minBuf)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(fullPcm, 0, fullPcm.size)
                track.play()

                Thread.sleep(durationMs + 60L)
                track.stop()
                track.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Simulates a real 250ms game vibration on the phone's motor so the user can see
     * the sensor detect it and fire the sound automatically!
     */
    fun simulateGameVibration(context: Context) {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(260L, 255))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(260L)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun synthesizeWaveform(
        preset: SoundPreset,
        volume: Float,
        pitch: Float
    ): ShortArray {
        val durationMs = when (preset) {
            SoundPreset.GAME_PING -> 220
            SoundPreset.ALERT_DOUBLE -> 260
            SoundPreset.BELL_STRIKE -> 280
            SoundPreset.CYBER_PULSE -> 180
            SoundPreset.MECHANICAL -> 35
            SoundPreset.DEEP_BASS -> 95
        }

        val numSamples = (SAMPLE_RATE * durationMs) / 1000
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / numSamples

            val rawSample: Double = when (preset) {
                SoundPreset.GAME_PING -> {
                    // Clear, bright dual-tone sonar ping (880Hz + 1760Hz harmonic)
                    val f1 = 1046.5 * pitch // C6
                    val f2 = 2093.0 * pitch // C7
                    val env = exp(-progress * 5.0)
                    (0.7 * sin(2.0 * PI * f1 * t) + 0.3 * sin(2.0 * PI * f2 * t)) * env
                }
                SoundPreset.ALERT_DOUBLE -> {
                    // Two distinct high-visibility beeps (first half 1200Hz, second half 1600Hz)
                    val inFirstBeep = progress < 0.42
                    val inSecondBeep = progress > 0.52
                    when {
                        inFirstBeep -> {
                            val f = 1180.0 * pitch
                            sin(2.0 * PI * f * t) * 0.9
                        }
                        inSecondBeep -> {
                            val f = 1560.0 * pitch
                            val localP = (progress - 0.52) / 0.48
                            sin(2.0 * PI * f * t) * exp(-localP * 2.5)
                        }
                        else -> 0.0
                    }
                }
                SoundPreset.BELL_STRIKE -> {
                    // Rich metallic bell chime (inharmonic partials)
                    val f0 = 1318.5 * pitch
                    val p1 = sin(2.0 * PI * f0 * t)
                    val p2 = 0.5 * sin(2.0 * PI * f0 * 1.5 * t) * exp(-progress * 4.0)
                    val p3 = 0.25 * sin(2.0 * PI * f0 * 2.42 * t) * exp(-progress * 8.0)
                    val env = exp(-progress * 4.2)
                    ((p1 + p2 + p3) / 1.75) * env
                }
                SoundPreset.CYBER_PULSE -> {
                    // Ascending FM synth chord
                    val f = (650.0 + 750.0 * progress) * pitch
                    val mod = sin(2.0 * PI * 180.0 * t) * 2.2
                    val env = sin(PI * progress) * exp(-progress * 1.8)
                    sin(2.0 * PI * f * t + mod) * env
                }
                SoundPreset.MECHANICAL -> {
                    // Sharp tactile click
                    val freq = 1450.0 * pitch
                    val noise = (Random.nextDouble() * 2.0 - 1.0) * exp(-progress * 14.0)
                    val tone = sin(2.0 * PI * freq * t) * exp(-progress * 8.0)
                    0.6 * tone + 0.4 * noise
                }
                SoundPreset.DEEP_BASS -> {
                    // Punchy sub-bass impact
                    val freq = (140.0 - 80.0 * progress) * pitch
                    val env = exp(-progress * 3.8)
                    sin(2.0 * PI * freq * t) * env
                }
            }

            val scaled = (rawSample * volume * Short.MAX_VALUE).toInt()
            samples[i] = scaled.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return samples
    }
}
