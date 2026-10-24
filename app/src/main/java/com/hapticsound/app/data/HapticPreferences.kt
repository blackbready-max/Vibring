package com.hapticsound.app.data

import android.content.Context
import android.content.SharedPreferences

enum class SoundPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String
) {
    GAME_PING(
        id = "game_ping",
        title = "Игровой сонар (Ping)",
        subtitle = "Звонкий пробивной сигнал, отлично слышно поверх игры",
        emoji = "🔔"
    ),
    ALERT_DOUBLE(
        id = "alert_double",
        title = "Двойной сигнал тревоги",
        subtitle = "Громкий двойной бип для важных событий и вибраций",
        emoji = "🚨"
    ),
    BELL_STRIKE(
        id = "bell_strike",
        title = "Звонкий колокольчик",
        subtitle = "Чистый металлический резонанс с долгим затуханием",
        emoji = "🎣"
    ),
    CYBER_PULSE(
        id = "cyber_pulse",
        title = "Кибер-импульс",
        subtitle = "Мощный футуристичный синтезаторный аккорд",
        emoji = "⚡"
    ),
    MECHANICAL(
        id = "mechanical",
        title = "Механический щелчок",
        subtitle = "Четкий тактильный клик в стиле Taptic Engine",
        emoji = "⚙️"
    ),
    DEEP_BASS(
        id = "deep_bass",
        title = "Глубокий бас-удар",
        subtitle = "Плотный низкочастотный акустический отклик",
        emoji = "🥁"
    );

    companion object {
        fun fromId(id: String): SoundPreset {
            return values().find { it.id == id } ?: GAME_PING
        }
    }
}

enum class DetectionMode(
    val id: String,
    val title: String,
    val description: String
) {
    MOTOR_IN_HANDS(
        id = "motor_in_hands",
        title = "В руках (Фильтр вибромотора)",
        description = "Ловит высокочастотную дрожь моторчика в игре, игнорируя плавные наклоны телефона в руках"
    ),
    ON_TABLE_ULTRA(
        id = "on_table_ultra",
        title = "На столе (Макс. чувствительность)",
        description = "Срабатывает на малейшую микровибрацию корпуса, когда телефон лежит неподвижно"
    );

    companion object {
        fun fromId(id: String): DetectionMode {
            return values().find { it.id == id } ?: MOTOR_IN_HANDS
        }
    }
}

enum class RepeatCount(val count: Int, val label: String) {
    ONCE(1, "1 раз"),
    TWICE(2, "2 раза"),
    TRIPLE(3, "3 раза (Тревога)");

    companion object {
        fun fromCount(c: Int): RepeatCount = values().find { it.count == c } ?: ONCE
    }
}

class HapticPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("haptic_game_sound_prefs", Context.MODE_PRIVATE)

    var isMasterEnabled: Boolean
        get() = prefs.getBoolean("master_enabled", true)
        set(value) = prefs.edit().putBoolean("master_enabled", value).apply()

    var enableGameVibrationDetector: Boolean
        get() = prefs.getBoolean("enable_game_vib_detector", true)
        set(value) = prefs.edit().putBoolean("enable_game_vib_detector", value).apply()

    var detectionMode: DetectionMode
        get() = DetectionMode.fromId(prefs.getString("detection_mode", DetectionMode.MOTOR_IN_HANDS.id) ?: DetectionMode.MOTOR_IN_HANDS.id)
        set(value) = prefs.edit().putString("detection_mode", value.id).apply()

    // Threshold in m/s^2 of high-frequency motor oscillation energy (0.04f .. 1.50f)
    var vibrationThreshold: Float
        get() = prefs.getFloat("vibration_threshold", 0.22f)
        set(value) = prefs.edit().putFloat("vibration_threshold", value.coerceIn(0.03f, 2.0f)).apply()

    // Cooldown in milliseconds between triggers (300ms .. 4000ms)
    var cooldownMs: Long
        get() = prefs.getLong("cooldown_ms", 800L)
        set(value) = prefs.edit().putLong("cooldown_ms", value.coerceIn(250L, 5000L)).apply()

    var selectedPreset: SoundPreset
        get() = SoundPreset.fromId(prefs.getString("selected_preset", SoundPreset.GAME_PING.id) ?: SoundPreset.GAME_PING.id)
        set(value) = prefs.edit().putString("selected_preset", value.id).apply()

    var repeatCount: RepeatCount
        get() = RepeatCount.fromCount(prefs.getInt("repeat_count", 1))
        set(value) = prefs.edit().putInt("repeat_count", value.count).apply()

    var volume: Float
        get() = prefs.getFloat("volume", 0.95f)
        set(value) = prefs.edit().putFloat("volume", value.coerceIn(0.05f, 1.0f)).apply()

    var pitch: Float
        get() = prefs.getFloat("pitch", 1.0f)
        set(value) = prefs.edit().putFloat("pitch", value.coerceIn(0.6f, 1.6f)).apply()

    var useAlarmChannel: Boolean
        get() = prefs.getBoolean("use_alarm_channel", false)
        set(value) = prefs.edit().putBoolean("use_alarm_channel", value).apply()

    var enableUiClicks: Boolean
        get() = prefs.getBoolean("enable_ui_clicks", false)
        set(value) = prefs.edit().putBoolean("enable_ui_clicks", value).apply()
}
