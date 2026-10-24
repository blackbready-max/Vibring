package com.hapticsound.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hapticsound.app.audio.SoundEngine
import com.hapticsound.app.data.*
import com.hapticsound.app.sensor.VibrationSensorEngine
import com.hapticsound.app.service.HapticForegroundService
import com.hapticsound.app.ui.theme.*
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val prefs = HapticPreferences(this)
        VibrationSensorEngine.start(this)
        if (prefs.isMasterEnabled) {
            try {
                HapticForegroundService.start(this)
            } catch (_: Exception) {
            }
        }

        setContent {
            HapticSoundTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgObsidian
                ) {
                    GameVibrationDashboard()
                }
            }
        }
    }
}

@Composable
fun GameVibrationDashboard() {
    val context = LocalContext.current
    val prefs = remember { HapticPreferences(context) }

    var masterEnabled by remember { mutableStateOf(prefs.isMasterEnabled) }
    var detectionMode by remember { mutableStateOf(prefs.detectionMode) }
    var threshold by remember { mutableStateOf(prefs.vibrationThreshold) }
    var cooldownMs by remember { mutableStateOf(prefs.cooldownMs) }

    var selectedPreset by remember { mutableStateOf(prefs.selectedPreset) }
    var repeatCount by remember { mutableStateOf(prefs.repeatCount) }
    var volume by remember { mutableStateOf(prefs.volume) }
    var pitch by remember { mutableStateOf(prefs.pitch) }
    var useAlarmChannel by remember { mutableStateOf(prefs.useAlarmChannel) }
    var enableUiClicks by remember { mutableStateOf(prefs.enableUiClicks) }

    val liveVibLevel by VibrationSensorEngine.liveVibrationLevel.collectAsState()
    val triggerCount by VibrationSensorEngine.triggerCount.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgObsidian)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 20.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GAME VIBRATION DETECTOR",
                        style = MaterialTheme.typography.labelLarge,
                        color = AccentEmerald
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Звук на вибрацию в игре",
                        style = MaterialTheme.typography.headlineLarge
                    )
                }

                Switch(
                    checked = masterEnabled,
                    onCheckedChange = { enabled ->
                        masterEnabled = enabled
                        prefs.isMasterEnabled = enabled
                        try {
                            if (enabled) {
                                HapticForegroundService.start(context)
                                VibrationSensorEngine.start(context)
                            } else {
                                HapticForegroundService.stop(context)
                            }
                        } catch (_: Exception) {
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextPrimary,
                        checkedTrackColor = AccentEmerald
                    )
                )
            }
        }

        // Live Vibration Sensor Oscilloscope & Calibration Card
        item {
            val isTriggeredNow = liveVibLevel >= threshold
            val barColor = if (isTriggeredNow) AccentEmerald else AccentIndigoLight
            val normalizedBar = (liveVibLevel / 1.0f).coerceIn(0f, 1f)
            val normalizedThreshold = (threshold / 1.0f).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .border(
                        width = 1.5.dp,
                        color = if (isTriggeredNow) AccentEmerald else BorderColor,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (masterEnabled) AccentEmerald else AccentRose)
                            )
                            Text(
                                text = "Датчик вибромотора (в реальном времени)",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Text(
                            text = "Сработал: $triggerCount",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AccentEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Live Meter Bar
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = String.format("Текущая вибрация: %.2f", liveVibLevel),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isTriggeredNow) AccentEmerald else TextSecondary
                            )
                            Text(
                                text = String.format("Порог: %.2f", threshold),
                                style = MaterialTheme.typography.bodyMedium,
                                color = AccentAmber
                            )
                        }

                        LinearProgressIndicator(
                            progress = normalizedBar,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = barColor,
                            trackColor = SurfaceElevated
                        )
                    }

                    // Threshold Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Чувствительность (порог срабатывания)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (threshold <= 0.12f) "Макс." else if (threshold <= 0.30f) "Оптимально" else "Низкая",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AccentIndigoLight
                            )
                        }
                        Text(
                            text = "Чем меньше значение, тем слабее вибрацию в игре ловит датчик",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = threshold,
                            onValueChange = {
                                threshold = it
                                prefs.vibrationThreshold = it
                            },
                            valueRange = 0.04f..0.80f,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentAmber,
                                activeTrackColor = AccentAmber,
                                inactiveTrackColor = SurfaceElevated
                            )
                        )
                    }

                    // Test Game Vibration Button
                    Button(
                        onClick = {
                            // Vibrate phone motor like a game -> sensor will catch it and play the sound!
                            SoundEngine.simulateGameVibration(context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentEmerald,
                            contentColor = BgObsidian
                        )
                    ) {
                        Text(
                            text = "📳 Симулировать вибрацию игры (Проверка)",
                            style = MaterialTheme.typography.titleLarge,
                            color = BgObsidian,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Detection Mode Selection (In Hands vs On Table)
        item {
            Text(
                text = "РЕЖИМ РАБОТЫ ДАТЧИКА",
                style = MaterialTheme.typography.labelLarge,
                color = TextMuted
            )
        }

        items(DetectionMode.values()) { mode ->
            val isSelected = mode == detectionMode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) SurfaceElevated else SurfaceDark)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) AccentIndigo else BorderColor,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        detectionMode = mode
                        prefs.detectionMode = mode
                        if (mode == DetectionMode.ON_TABLE_ULTRA && threshold > 0.12f) {
                            threshold = 0.08f
                            prefs.vibrationThreshold = 0.08f
                        } else if (mode == DetectionMode.MOTOR_IN_HANDS && threshold < 0.12f) {
                            threshold = 0.22f
                            prefs.vibrationThreshold = 0.22f
                        }
                    }
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mode.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = if (isSelected) TextPrimary else TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = mode.description,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    RadioButton(
                        selected = isSelected,
                        onClick = {
                            detectionMode = mode
                            prefs.detectionMode = mode
                        },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = AccentIndigo,
                            unselectedColor = TextMuted
                        )
                    )
                }
            }
        }

        // Volume, Repeat Count & Pitch Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderColor, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Громкость сигнала",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "${(volume * 100).roundToInt()}%",
                            style = MaterialTheme.typography.titleLarge,
                            color = AccentIndigoLight
                        )
                    }

                    Slider(
                        value = volume,
                        onValueChange = {
                            volume = it
                            prefs.volume = it
                        },
                        onValueChangeFinished = {
                            SoundEngine.playSound(selectedPreset, volume, pitch, repeatCount.count, useAlarmChannel)
                        },
                        valueRange = 0.05f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentIndigoLight,
                            activeTrackColor = AccentIndigo,
                            inactiveTrackColor = SurfaceElevated
                        )
                    )

                    HorizontalDivider(color = BorderColor)

                    Text(
                        text = "Повтор сигнала при вибрации",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RepeatCount.values().forEach { rc ->
                            val selected = rc == repeatCount
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) AccentIndigo else SurfaceElevated)
                                    .clickable {
                                        repeatCount = rc
                                        prefs.repeatCount = rc
                                        SoundEngine.playSound(selectedPreset, volume, pitch, rc.count, useAlarmChannel)
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = rc.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = BorderColor)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Пауза между срабатываниями",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = String.format("%.1f сек", cooldownMs / 1000f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = AccentIndigoLight
                        )
                    }

                    Slider(
                        value = cooldownMs.toFloat(),
                        onValueChange = {
                            cooldownMs = it.toLong()
                            prefs.cooldownMs = cooldownMs
                        },
                        valueRange = 300f..3000f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentIndigoLight,
                            activeTrackColor = AccentIndigo,
                            inactiveTrackColor = SurfaceElevated
                        )
                    )
                }
            }
        }

        // Sound Presets Section
        item {
            Text(
                text = "ВЫБЕРИТЕ ЗВУК ДЛЯ ВИБРАЦИИ В ИГРЕ",
                style = MaterialTheme.typography.labelLarge,
                color = TextMuted
            )
        }

        items(SoundPreset.values()) { preset ->
            val isSelected = preset == selectedPreset
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) SurfaceElevated else SurfaceDark)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) AccentIndigo else BorderColor,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable {
                        selectedPreset = preset
                        prefs.selectedPreset = preset
                        SoundEngine.playSound(
                            preset = preset,
                            volume = volume,
                            pitch = pitch,
                            repeatCount = repeatCount.count,
                            useAlarmStream = useAlarmChannel
                        )
                    }
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = preset.emoji,
                        fontSize = 26.sp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = preset.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = if (isSelected) TextPrimary else TextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = preset.subtitle,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    RadioButton(
                        selected = isSelected,
                        onClick = {
                            selectedPreset = preset
                            prefs.selectedPreset = preset
                            SoundEngine.playSound(
                                preset = preset,
                                volume = volume,
                                pitch = pitch,
                                repeatCount = repeatCount.count,
                                useAlarmStream = useAlarmChannel
                            )
                        },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = AccentIndigo,
                            unselectedColor = TextMuted
                        )
                    )
                }
            }
        }

        // Extra Options
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderColor, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Играть через канал Будильника",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Звук будет слышен, даже если громкость медиа или игры убавлена",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Switch(
                            checked = useAlarmChannel,
                            onCheckedChange = {
                                useAlarmChannel = it
                                prefs.useAlarmChannel = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TextPrimary,
                                checkedTrackColor = AccentIndigo
                            )
                        )
                    }
                }
            }
        }
    }
}
