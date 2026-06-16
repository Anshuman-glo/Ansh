package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.*
import com.example.viewmodel.AnshViewModel
import kotlinx.coroutines.launch
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnshDashboard(viewModel: AnshViewModel) {
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Scaffold(
        bottomBar = {
            AnshBottomNavBar(
                activeTab = activeTab,
                onTabSelected = { viewModel.setActiveTab(it) }
            )
        },
        containerColor = CyberBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(CyberBackground, Color(0xFF110E15))
                    )
                )
        ) {
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "TabSwitcher"
            ) { tab ->
                when (tab) {
                    "dashboard" -> DashboardView(viewModel)
                    "assistant" -> AssistantView(viewModel)
                    "settings" -> SettingsRulesView(viewModel)
                }
            }
        }
    }
}

// --- Navigation Elements ---

@Composable
fun AnshBottomNavBar(
    activeTab: String,
    onTabSelected: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        color = CyberSurface,
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 8.dp,
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val items = listOf(
                NavBarItem("dashboard", "SYSTEM", Icons.Default.Dashboard),
                NavBarItem("assistant", "BRAIN AI", Icons.Default.ChatBubble),
                NavBarItem("settings", "SECURITY", Icons.Default.Shield)
            )

            items.forEach { item ->
                val isSelected = activeTab == item.id
                val tint by animateColorAsState(
                    targetValue = if (isSelected) ImmersivePrimary else CoolGray,
                    label = "NavColor"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(item.id) }
                        .testTag("nav_${item.id}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.label,
                        color = tint,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

data class NavBarItem(val id: String, val label: String, val icon: ImageVector)

// --- Dashboard Component (SYSTEM & TELEMETRY) ---

@Composable
fun DashboardView(viewModel: AnshViewModel) {
    val state by viewModel.deviceState.collectAsStateWithLifecycle()
    val logs by viewModel.systemLogs.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Identity Header
        item {
            AnshHeaderSection()
        }

        // Circular Telemetry Meters Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TelemetryCircleMeter(
                        label = "BATTERY",
                        value = state.batteryLevel,
                        maxVal = 100,
                        unit = "%",
                        activeColor = if (state.batteryLevel < 25) ImmersiveTertiary else ImmersivePrimary,
                        infoText = if (state.isCharging) "Charging" else "Discharging"
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    TelemetryBarGauge(
                        label = "STORAGE GB",
                        used = state.internalUsedGb,
                        total = state.internalTotalGb,
                        color = ImmersiveSecondary
                    )
                }
            }
        }

        // Quick Controls
        item {
            Text(
                text = "SYSTEM CONTROLLERS",
                color = ImmersivePrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurfaceCard),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Battery manual slide and charging switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Simulate Power Status", color = IceWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Battery level slider", color = CoolGray, fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Charging", color = if (state.isCharging) ImmersivePrimary else CoolGray, fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp))
                            Switch(
                                checked = state.isCharging,
                                onCheckedChange = { viewModel.toggleChargingState() },
                                colors = SwitchDefaults.colors(checkedThumbColor = ImmersivePrimary)
                            )
                        }
                    }

                    Slider(
                        value = state.batteryLevel.toFloat(),
                        onValueChange = { viewModel.setBatteryLevel(it.toInt()) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = ImmersivePrimary,
                            activeTrackColor = ImmersivePrimary,
                            inactiveTrackColor = Color(0x22FFFFFF)
                        )
                    )

                    Divider(color = GlassBorderSemi)

                    // Power saving toggle & Work sandbox toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            AnshToggleButton(
                                label = "Power Saver",
                                isActive = state.powerSavingMode,
                                onToggle = { viewModel.setPowerSaving(!state.powerSavingMode) },
                                activeIcon = Icons.Default.BatterySaver,
                                inactiveIcon = Icons.Default.BatteryFull
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            AnshToggleButton(
                                label = "Work Space",
                                isActive = state.isWorkProfileActive,
                                onToggle = { viewModel.toggleWorkProfile() },
                                activeIcon = Icons.Default.Work,
                                inactiveIcon = Icons.Default.WorkOutline
                            )
                        }
                    }
                }
            }
        }

        // Hardware Controls (Display & Sound)
        item {
            Text(
                text = "HARDWARE TELEMENTRY",
                color = ImmersivePrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurfaceCard),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Brightness slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.BrightnessMedium, contentDescription = null, tint = ImmersivePrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Screen Brightness", color = IceWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            Text("${Math.round(state.brightness * 100)}%", color = CoolGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Slider(
                                value = state.brightness,
                                onValueChange = { viewModel.setBrightness(it) },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = ImmersivePrimary,
                                    activeTrackColor = ImmersivePrimary,
                                    inactiveTrackColor = Color(0x22FFFFFF)
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            IconButton(onClick = { viewModel.toggleAutoBrightness() }) {
                                Icon(
                                    imageVector = if (state.autoBrightness) Icons.Default.BrightnessAuto else Icons.Default.Brightness6,
                                    contentDescription = "Auto Brightness",
                                    tint = if (state.autoBrightness) ImmersivePrimary else CoolGray,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Divider(color = GlassBorderSemi)

                    // Volume controller
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (state.isMute) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = if (state.isMute) ImmersiveTertiary else ImmersivePrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Audio Media Volume", color = IceWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            Text("${state.mediaVolume}/15", color = CoolGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Slider(
                                value = state.mediaVolume.toFloat(),
                                onValueChange = { viewModel.setMediaVolume(it.toInt()) },
                                valueRange = 0f..15f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = ImmersivePrimary,
                                    activeTrackColor = ImmersivePrimary,
                                    inactiveTrackColor = Color(0x22FFFFFF)
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            IconButton(onClick = { viewModel.toggleMuteState() }) {
                                Icon(
                                    imageVector = if (state.isMute) Icons.Default.VolumeOff else Icons.Default.VolumeMute,
                                    contentDescription = "Mute Toggle",
                                    tint = if (state.isMute) ImmersiveTertiary else CoolGray
                                )
                            }
                        }
                    }

                    Divider(color = GlassBorderSemi)

                    // Buttons Wifi, bluetooth, haptic
                    Row {
                        IconButton(
                            onClick = { viewModel.toggleWifi() },
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (state.wifiOn) Color(0xFF381E72).copy(alpha = 0.45f) else Color.Transparent)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (state.wifiOn) Icons.Default.Wifi else Icons.Default.WifiOff,
                                    contentDescription = "Wifi",
                                    tint = if (state.wifiOn) ImmersivePrimary else CoolGray
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.toggleBluetooth() },
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (state.bluetoothOn) Color(0xFF381E72).copy(alpha = 0.45f) else Color.Transparent)
                        ) {
                            Icon(
                                imageVector = if (state.bluetoothOn) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                                  contentDescription = "Bluetooth",
                                tint = if (state.bluetoothOn) ImmersivePrimary else CoolGray
                            )
                        }
                        IconButton(
                            onClick = { viewModel.toggleVibrateState() },
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (state.isVibrate) Color(0xFF381E72).copy(alpha = 0.45f) else Color.Transparent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Vibration,
                                contentDescription = "Vibrate",
                                tint = if (state.isVibrate) ImmersivePrimary else CoolGray
                            )
                        }
                        IconButton(
                            onClick = { viewModel.toggleCameraFlash() },
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (state.cameraFlashOn) Color(0xFF4F378B).copy(alpha = 0.45f) else Color.Transparent)
                        ) {
                            Icon(
                                imageVector = if (state.cameraFlashOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                                contentDescription = "Flashlight",
                                tint = if (state.cameraFlashOn) ImmersiveTertiary else CoolGray
                            )
                        }
                    }
                }
            }
        }

        // Pedometer health activity tracker
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurfaceCard),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsRun, contentDescription = "Pedometer", tint = ImmersivePrimary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Pedometer & Vitals", color = IceWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("${state.stepCount} Steps | ${state.heartRate} BPM", color = CoolGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.incrementSteps() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72).copy(alpha = 0.35f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Walk", color = IceWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Button(
                            onClick = { viewModel.measureHeartRate() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F378B).copy(alpha = 0.35f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("ECG", color = ImmersiveTertiary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // Network telemetry & connection speeds
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurfaceCard),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = "Speed", tint = ImmersiveSecondary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Network Speed Test", color = IceWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Current: ${state.netSpeedDownload}", color = ImmersiveSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    Button(
                        onClick = { viewModel.runNetworkSpeedTest() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72).copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, ImmersiveSecondary.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("TEST", color = IceWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // Recycle / sector optimizer
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurfaceCard),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = ImmersiveTertiary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Storage Optimizer", color = IceWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Trash Caches: ${state.recycleBinMb} MB", color = CoolGray, fontSize = 11.sp)
                            }
                        }
                        IconButton(onClick = { viewModel.emptyRecycleBin() }) {
                            Icon(Icons.Default.DeleteForever, contentDescription = "Clean Caches", tint = ImmersiveTertiary)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.runDuplicateFileFinder() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72).copy(alpha = 0.35f))
                        ) {
                            Text("Cleans duplicates (${state.duplicateFiles})", color = ImmersivePrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Button(
                            onClick = { viewModel.runLargeFileScanner() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F378B).copy(alpha = 0.35f))
                        ) {
                            Text("Scan space sizes", color = ImmersiveSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // Rolling Kernel System Console Logs
        item {
            Text(
                text = "ANSH OS KERNEL DIAGNOSTIC CONSOLE",
                color = ImmersiveTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                color = Color(0xFF020204),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF2E1929))
            ) {
                LazyColumn(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            color = if (log.contains("Automation:") || log.contains("OCR")) ImmersivePrimary else if (log.contains("ACCESS") || log.contains("DENIED")) ImmersiveTertiary else Color(0xFF00FFCC),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnshHeaderSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "ANSH",
                color = ImmersivePrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 3.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(ImmersivePrimary)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "OFFLINE CORES STANDBY",
                    color = CoolGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }
        
        Surface(
            color = Color(0xFF381E72),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, ImmersivePrimary)
        ) {
            Text(
                text = "v3.1-PRO",
                color = ImmersivePrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun TelemetryCircleMeter(
    label: String,
    value: Int,
    maxVal: Int,
    unit: String,
    activeColor: Color,
    infoText: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberSurfaceCard),
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.height(180.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                label,
                fontSize = 11.sp,
                color = CoolGray,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(90.dp)
            ) {
                // Background Circle Track
                Canvas(modifier = Modifier.size(80.dp)) {
                    drawArc(
                        color = Color(0x22FFFFFF),
                        startAngle = -220f,
                        sweepAngle = 260f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Foreground active track
                val fraction = value.toFloat() / maxVal.toFloat()
                val sweep = fraction * 260f
                Canvas(modifier = Modifier.size(80.dp)) {
                    drawArc(
                        color = activeColor,
                        startAngle = -220f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$value$unit",
                        color = IceWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = infoText,
                        color = activeColor,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun TelemetryBarGauge(
    label: String,
    used: Float,
    total: Float,
    color: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberSurfaceCard),
        border = BorderStroke(1.dp, GlassBorder),
        modifier = Modifier.height(180.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = CoolGray,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${Math.round(used)} GB / ${Math.round(total)} GB",
                    color = IceWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${Math.round((total - used))} GB Available",
                    color = color,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val fraction = used / total
            Column {
                LinearProgressIndicator(
                    progress = fraction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = color,
                    trackColor = Color(0x22FFFFFF)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "System diagnostics nominal",
                    color = CoolGray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun AnshToggleButton(
    label: String,
    isActive: Boolean,
    onToggle: () -> Unit,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector
) {
    val bg = if (isActive) Color(0xFF381E72).copy(alpha = 0.35f) else Color(0x0AFFFFFF)
    val textC = if (isActive) ImmersivePrimary else IceWhite
    val borderC = if (isActive) ImmersivePrimary else GlassBorder

    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = BorderStroke(1.dp, borderC),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isActive) activeIcon else inactiveIcon,
                contentDescription = null,
                tint = textC,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                color = textC,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// --- Smart Chat Assistant View (WITH HIGH THINKING) ---

@Composable
fun AssistantView(viewModel: AnshViewModel) {
    val chatList by viewModel.chatHistory.collectAsStateWithLifecycle()
    val loading by viewModel.chatLoading.collectAsStateWithLifecycle()
    val thinkingText by viewModel.thinkingText.collectAsStateWithLifecycle()
    
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val listState = remember { androidx.compose.foundation.lazy.LazyListState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = results?.firstOrNull() ?: ""
            if (text.isNotBlank()) {
                query = text
                viewModel.sendAssistantMessage(text)
                query = ""
            }
        }
    }

    // Auto-scroll chats on load
    LaunchedEffect(chatList.size, loading) {
        if (chatList.isNotEmpty()) {
            listState.animateScrollToItem(chatList.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Chat Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "COGNITIVE CORES",
                    color = ImmersivePrimary,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    "Gemini-3.1-pro-preview (Thinking: HIGH)",
                    color = CoolGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            IconButton(onClick = { viewModel.clearChat() }) {
                Icon(Icons.Default.Delete, contentDescription = "Clear Chat", tint = ImmersiveTertiary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Predefined diagnostic tags for fast chat triggers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val suggestions = listOf(
                "Optimize my local storage usage",
                "Analyze active database rules",
                "Review battery health diagnostic logs",
                "Simulate step counts addition"
            )
            suggestions.forEach { s ->
                SuggestionChip(
                    onClick = { viewModel.sendAssistantMessage(s) },
                    label = { Text(s, color = ImmersivePrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = Color(0xFF132F2A)
                    ),
                    border = BorderStroke(1.dp, ImmersivePrimary)
                )
            }
        }

        // Active chat logs list
        Box(modifier = Modifier.weight(1f)) {
            if (chatList.isEmpty() && !loading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = ImmersivePrimary.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Ansh Intelligence Active.",
                        color = IceWhite,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "Ask anything to query device telemetry tables or run system automated steps offline.",
                        color = CoolGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(chatList) { chat ->
                        ChatBubbleCard(chat)
                    }

                    if (loading) {
                        item {
                            ThinkingLoaderCard(thinkingText)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Prompt input block
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                textStyle = LocalTextStyle.current.copy(color = IceWhite, fontSize = 14.sp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input"),
                placeholder = { Text("Command Ansh assistant...", color = CoolGray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ImmersivePrimary,
                    unfocusedBorderColor = GlassBorderSemi,
                    focusedContainerColor = CyberSurfaceCard,
                    unfocusedContainerColor = CyberSurfaceCard
                ),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (query.isNotBlank()) {
                        viewModel.sendAssistantMessage(query)
                        query = ""
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                })
            )

            IconButton(
                onClick = {
                    val voiceIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Ansh...")
                    }
                    try {
                        speechRecognizerLauncher.launch(voiceIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Google Speech Recognition is not supported on this device.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF381E72).copy(alpha = 0.35f))
                    .border(BorderStroke(1.dp, ImmersivePrimary.copy(alpha = 0.5f)), RoundedCornerShape(12.dp))
                    .testTag("chat_voice_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Input",
                    tint = ImmersivePrimary
                )
            }

            IconButton(
                onClick = {
                    if (query.isNotBlank()) {
                        viewModel.sendAssistantMessage(query)
                        query = ""
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                },
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ImmersivePrimary)
                    .testTag("chat_send_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = CoreGreen
                )
            }
        }
    }
}

@Composable
fun ChatBubbleCard(chat: ChatHistory) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Query bubble (User)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp))
                    .background(Color(0xFF20263F))
                    .padding(12.dp)
            ) {
                Text(
                    text = chat.query,
                    color = IceWhite,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Response bubble (Ansh Assistant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp))
                    .background(CyberSurfaceCard)
                    .border(BorderStroke(1.dp, Color(0xFF1B2238)), RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = ImmersivePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ANSH BRAIN ENGINE",
                        color = ImmersivePrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (chat.isThinking && chat.thinkingLog.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFF030408),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFF261923)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(ImmersiveTertiary))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("COGNITIVE LOGS", color = ImmersiveTertiary, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(chat.thinkingLog, color = CoolGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = chat.response,
                    color = IceWhite,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun ThinkingLoaderCard(thinkingStep: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(16.dp))
                .background(CyberSurfaceCard)
                .border(BorderStroke(1.dp, ImmersivePrimary.copy(alpha = 0.3f)), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = ImmersivePrimary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ANSH CORES THINKING...",
                    color = ImmersivePrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = thinkingStep,
                color = CoolGray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp
            )
        }
    }
}



// --- Automation Rules & PIN Security View ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRulesView(viewModel: AnshViewModel) {
    val activeRules by viewModel.rules.collectAsStateWithLifecycle()
    val activeNotes by viewModel.notes.collectAsStateWithLifecycle()
    val vaultLocked by viewModel.vaultLocked.collectAsStateWithLifecycle()

    var showCreateRuleByDialog by remember { mutableStateOf(false) }
    var ruleTitle by remember { mutableStateOf("") }
    var selectedTrigger by remember { mutableStateOf("Battery < 25%") }
    var selectedAction by remember { mutableStateOf("Enable Power Saving") }

    var pinInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    // Trigger lists
    val triggers = listOf("Battery < 25%", "Charging Connected")
    val actions = listOf("Enable Power Saving", "Mute audio")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
        item {
            Column {
                Text(
                    "AUTOMATIONS & STORAGE",
                    color = ImmersivePrimary,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    "Virtual triggers & SQLite secure encrypted partitions",
                    color = CoolGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // --- Continuous Vocal Background Floating AI Companion Core ---
        item {
            val context = LocalContext.current
            val isServiceActive by viewModel.floatingActive.collectAsStateWithLifecycle()
            val currentWakeWord by viewModel.wakeWord.collectAsStateWithLifecycle()
            
            var localWakeInput by remember(currentWakeWord) { mutableStateOf(currentWakeWord) }
            
            // Check Overlay (Draw over other apps) Permission
            var canDrawOverlay by remember {
                mutableStateOf(
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        android.provider.Settings.canDrawOverlays(context)
                    } else {
                        true
                    }
                )
            }
            
            // Check Microphone Permission
            var hasMicPermission by remember {
                mutableStateOf(
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.RECORD_AUDIO
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                )
            }
            
            val micPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                hasMicPermission = isGranted
            }

            // Periodic permission sync when coming back in focus
            LaunchedEffect(isServiceActive) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    canDrawOverlay = android.provider.Settings.canDrawOverlays(context)
                }
                hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "ANSH FLOATING COMPANION",
                    color = ImmersivePrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberSurfaceCard),
                    border = BorderStroke(1.dp, if (isServiceActive) ImmersivePrimary else GlassBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Enable Overlay Companion",
                                    color = IceWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Renders a draggable pulsing portal to capture hands-free background conversation.",
                                    color = CoolGray,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = isServiceActive,
                                onCheckedChange = {
                                    if (it) {
                                        // Ensure permissions
                                        if (!canDrawOverlay) {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                                val intent = Intent(
                                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    android.net.Uri.parse("package:${context.packageName}")
                                                )
                                                context.startActivity(intent)
                                                Toast.makeText(context, "Please enable 'Draw over other apps' first.", Toast.LENGTH_LONG).show()
                                            }
                                        } else if (!hasMicPermission) {
                                            micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                        } else {
                                            viewModel.toggleFloatingService()
                                        }
                                    } else {
                                        viewModel.toggleFloatingService()
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = ImmersivePrimary)
                            )
                        }

                        HorizontalDivider(color = GlassBorderSemi)

                        // Wake word configuration
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "ACOUSTIC WAKE KEYWORD",
                                color = ImmersiveSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "Ansh continuously parses spoken audio. It will ignore normal conversation until this custom keyword is uttered.",
                                color = CoolGray,
                                fontSize = 11.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = localWakeInput,
                                    onValueChange = { localWakeInput = it },
                                    textStyle = LocalTextStyle.current.copy(color = IceWhite, fontSize = 13.sp),
                                    placeholder = { Text("Set wake keyword (e.g. Ansh)", color = CoolGray) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ImmersivePrimary,
                                        unfocusedBorderColor = GlassBorderSemi
                                    )
                                )

                                Button(
                                    onClick = {
                                        viewModel.updateWakeWord(localWakeInput)
                                        focusManager.clearFocus()
                                        Toast.makeText(context, "Wake keyword updated successfully!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ImmersivePrimary)
                                ) {
                                    Text("SAVE", color = CoreGreen, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }
                            }
                        }

                        // Authorize & Diagnostics warnings
                        if (!canDrawOverlay || !hasMicPermission) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2C191D))
                                    .border(BorderStroke(1.dp, Color(0xFF632E3A)), RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("REQUIRED ACTION DETECTED", color = Color(0xFFFF8A8A), fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                                }

                                if (!canDrawOverlay) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Overlay permission (Draw over other apps) missing.", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                        Button(
                                            onClick = {
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                                    val intent = Intent(
                                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                        android.net.Uri.parse("package:${context.packageName}")
                                                    )
                                                    context.startActivity(intent)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B1E2E)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Text("GRANT", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (!hasMicPermission) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Microphone audio permission missing.", color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                        Button(
                                            onClick = {
                                                micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B1E2E)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Text("AUTHORIZE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0C241E))
                                    .border(BorderStroke(1.dp, Color(0xFF135242)), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(CoreGreen)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Diagnostics ready. Standby wake word: '$currentWakeWord'",
                                    color = CoreGreen,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // Crypto-Vault app lock
        item {
            Text(
                "CRYPTO NOTES VAULT",
                color = ImmersiveTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurfaceCard),
                border = BorderStroke(1.dp, if (vaultLocked) Color(0xFF532439) else ImmersivePrimary)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (vaultLocked) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = ImmersiveTertiary, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Database Encryption Partition Locked", color = IceWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Standard override PIN key: 1234", color = CoolGray, fontSize = 11.sp)
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = pinInput,
                                    onValueChange = { pinInput = it },
                                    textStyle = LocalTextStyle.current.copy(color = IceWhite, fontSize = 13.sp),
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.weight(1f).testTag("vault_pin_input"),
                                    placeholder = { Text("Enter crypt-key override", color = CoolGray) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ImmersiveTertiary,
                                        unfocusedBorderColor = Color(0xFF381F2C)
                                    ),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        viewModel.unlockVault(pinInput)
                                        pinInput = ""
                                        focusManager.clearFocus()
                                    })
                                )

                                Button(
                                    onClick = {
                                        viewModel.unlockVault(pinInput)
                                        pinInput = ""
                                        focusManager.clearFocus()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ImmersiveTertiary),
                                    modifier = Modifier.height(52.dp).testTag("vault_unlock_button")
                                ) {
                                    Text("OPEN", color = IceWhite, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, tint = ImmersivePrimary, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Encrypted Sector ONLINE", color = ImmersivePrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                IconButton(onClick = { viewModel.lockVault() }) {
                                    Icon(Icons.Default.Lock, contentDescription = "Lock", tint = CoolGray)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("SECURE STORED NOTES VAULT", color = CoolGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            
                            Spacer(modifier = Modifier.height(8.dp))

                            // Show note creation & notes list
                            val secureNotes = activeNotes.filter { it.isLocked }
                            if (secureNotes.isEmpty()) {
                                Text("No locked memories secure index.", color = CoolGray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    secureNotes.forEach { sn ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = SolidCardColor),
                                            border = BorderStroke(1.dp, GlassBorder),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(sn.title, color = ImmersivePrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    IconButton(onClick = { viewModel.deleteNote(sn.id) }, modifier = Modifier.size(20.dp)) {
                                                        Icon(Icons.Default.Close, contentDescription = null, tint = ImmersiveTertiary, modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(sn.content, color = IceWhite, fontSize = 12.sp, lineHeight = 16.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            Divider(color = GlassBorderSemi, modifier = Modifier.padding(vertical = 12.dp))

                            // Add secure note section
                            var secureTitle by remember { mutableStateOf("") }
                            var secureContent by remember { mutableStateOf("") }

                            Text("CREATE CLASSIFIED MEM RECORD", color = CoolGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = secureTitle,
                                onValueChange = { secureTitle = it },
                                textStyle = LocalTextStyle.current.copy(color = IceWhite, fontSize = 13.sp),
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Clasified Subject Header", color = CoolGray) },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ImmersivePrimary, unfocusedBorderColor = GlassBorderSemi)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = secureContent,
                                onValueChange = { secureContent = it },
                                textStyle = LocalTextStyle.current.copy(color = IceWhite, fontSize = 13.sp),
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Secure Record Details...", color = CoolGray) },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ImmersivePrimary, unfocusedBorderColor = GlassBorderSemi)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (secureTitle.isNotBlank() && secureContent.isNotBlank()) {
                                        viewModel.addNote(secureTitle, secureContent, "Secure")
                                        secureTitle = ""
                                        secureContent = ""
                                        focusManager.clearFocus()
                                    }
                                },
                                modifier = Modifier.align(Alignment.End),
                                colors = ButtonDefaults.buttonColors(containerColor = ImmersivePrimary)
                            ) {
                                Text("COMMIT RECORD", color = CoreGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }

        // Rule Trigger Manager
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "INTELLIGENT TRIGGER RULES",
                    color = ImmersivePrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                Button(
                    onClick = { showCreateRuleByDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ImmersivePrimary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = CoreGreen, modifier = Modifier.size(14.dp))
                    Text("ADD", color = CoreGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
        }

        if (activeRules.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberSurfaceCard),
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No automation triggers defined.", color = CoolGray, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(activeRules) { rule ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberSurfaceCard),
                    border = BorderStroke(1.dp, if (rule.isActive) ImmersivePrimary.copy(alpha = 0.5f) else GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (rule.isActive) ImmersivePrimary else CoolGray)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = rule.title,
                                    color = IceWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Visual triggered logic
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("IF: ", color = ImmersiveTertiary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text(rule.triggerEvent, color = IceWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text("  THEN: ", color = ImmersivePrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text(rule.actionTask, color = IceWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = rule.isActive,
                                onCheckedChange = { viewModel.toggleRuleActive(rule) },
                                colors = SwitchDefaults.colors(checkedThumbColor = ImmersivePrimary)
                            )
                            IconButton(onClick = { viewModel.deleteRule(rule.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ImmersiveTertiary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // Quick Note Vault (Public notes folder in SQLite Room)
        item {
            Text(
                "UNSECURED MEMORY DUMP",
                color = ImmersivePrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        val publicNotes = activeNotes.filter { !it.isLocked }
        if (publicNotes.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberSurfaceCard),
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("No public logs.", color = CoolGray, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(publicNotes) { note ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberSurfaceCard),
                    border = BorderStroke(1.dp, GlassBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(note.title, color = ImmersivePrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            IconButton(onClick = { viewModel.deleteNote(note.id) }, modifier = Modifier.size(24.dp)) {
                                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, tint = ImmersiveTertiary, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(note.content, color = IceWhite, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }

    // Modal popup to create a trigger rule
    if (showCreateRuleByDialog) {
        var expandedTrigger by remember { mutableStateOf(false) }
        var expandedAction by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreateRuleByDialog = false },
            title = { Text("Insert Automation Trigger Rule", color = ImmersivePrimary, fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = ruleTitle,
                        onValueChange = { ruleTitle = it },
                        textStyle = LocalTextStyle.current.copy(color = IceWhite),
                        label = { Text("Enter Rule Identifier Label", color = CoolGray) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ImmersivePrimary, unfocusedBorderColor = Color(0xFF2A2E44))
                    )

                    // Manual Trigger drop downs
                    Column {
                        Text("Condition Trigger Scenario:", color = CoolGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { expandedTrigger = !expandedTrigger },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2135)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(selectedTrigger, color = IceWhite)
                            }
                            DropdownMenu(
                                expanded = expandedTrigger,
                                onDismissRequest = { expandedTrigger = false },
                                modifier = Modifier.background(CyberSurface)
                            ) {
                                triggers.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(t, color = IceWhite) },
                                        onClick = {
                                            selectedTrigger = t
                                            expandedTrigger = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Column {
                        Text("Action Script Sequence:", color = CoolGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { expandedAction = !expandedAction },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2135)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(selectedAction, color = IceWhite)
                            }
                            DropdownMenu(
                                expanded = expandedAction,
                                onDismissRequest = { expandedAction = false },
                                modifier = Modifier.background(CyberSurface)
                            ) {
                                actions.forEach { a ->
                                    DropdownMenuItem(
                                        text = { Text(a, color = IceWhite) },
                                        onClick = {
                                            selectedAction = a
                                            expandedAction = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (ruleTitle.isNotBlank()) {
                            viewModel.addRule(ruleTitle, selectedTrigger, selectedAction)
                            ruleTitle = ""
                            showCreateRuleByDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ImmersivePrimary)
                ) {
                    Text("Trigger Save", color = CoreGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateRuleByDialog = false }) {
                    Text("Cancel", color = ImmersiveTertiary)
                }
            },
            containerColor = CyberSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
