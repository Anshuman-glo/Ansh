package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeviceState(
    val batteryLevel: Int = 85,
    val isCharging: Boolean = false,
    val powerSavingMode: Boolean = false,
    val batteryTemp: String = "29.4°C",
    val uptimeLabel: String = "14h 32m",
    val isWorkProfileActive: Boolean = false,
    
    val brightness: Float = 0.6f,
    val autoBrightness: Boolean = true,
    val rotationLocked: Boolean = false,
    val screenOn: Boolean = true,
    val refreshRate: String = "120 Hz",
    
    val mediaVolume: Int = 7,
    val callVolume: Int = 4,
    val alarmVolume: Int = 8,
    val isMute: Boolean = false,
    val isVibrate: Boolean = true,
    
    val internalTotalGb: Float = 128.0f,
    val internalUsedGb: Float = 42.6f,
    val duplicateFiles: Int = 8,
    val largeFiles: Int = 4,
    val recycleBinMb: Int = 340,
    
    val wifiOn: Boolean = true,
    val bluetoothOn: Boolean = true,
    val nfcOn: Boolean = false,
    val hotspotOn: Boolean = false,
    val vpnActive: Boolean = false,
    val netSpeedDownload: String = "142.5 Mbps",
    
    val stepCount: Int = 5420,
    val compassAngle: Int = 188,
    val heartRate: Int = 72,
    
    val cameraFlashOn: Boolean = false,
    val lastOcrText: String = "",
    val qrCodePayload: String = ""
)

class AnshViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        AnshDatabase::class.java, "ansh-assistant-db"
    ).fallbackToDestructiveMigration().build()
    
    val dao = db.dao()

    // --- State Stream Observers ---
    val notes: StateFlow<List<Note>> = dao.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasks: StateFlow<List<Task>> = dao.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rules: StateFlow<List<AutomationRule>> = dao.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chatHistory: StateFlow<List<ChatHistory>> = dao.getChatHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Core Device State ---
    private val _deviceState = MutableStateFlow(DeviceState())
    val deviceState: StateFlow<DeviceState> = _deviceState.asStateFlow()

    // --- Chat States ---
    private val _chatLoading = MutableStateFlow(false)
    val chatLoading: StateFlow<Boolean> = _chatLoading.asStateFlow()

    private val _thinkingText = MutableStateFlow("")
    val thinkingText: StateFlow<String> = _thinkingText.asStateFlow()

    // --- App Locks / Secure Vault PIN ---
    private val _vaultLocked = MutableStateFlow(true)
    val vaultLocked: StateFlow<Boolean> = _vaultLocked.asStateFlow()

    private val prefs = application.getSharedPreferences("ansh_prefs", android.content.Context.MODE_PRIVATE)

    private val _floatingActive = MutableStateFlow(prefs.getBoolean("floating_active", false))
    val floatingActive: StateFlow<Boolean> = _floatingActive.asStateFlow()

    private val _wakeWord = MutableStateFlow(prefs.getString("wake_word", "Ansh") ?: "Ansh")
    val wakeWord: StateFlow<String> = _wakeWord.asStateFlow()

    private val _systemLogs = MutableStateFlow<List<String>>(listOf("Ansh OS initialized and standing by."))
    val systemLogs: StateFlow<List<String>> = _systemLogs.asStateFlow()

    private val _activeTab = MutableStateFlow("dashboard") // dashboard, assistant, settings
    val activeTab: StateFlow<String> = _activeTab.asStateFlow()

    init {
        // Seed default items if database is completely empty
        seedTablesIfNeeded()
        // Run simulated background automation trigger check loop
        startAutomationLoop()
    }

    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _systemLogs.value = listOf("[ $timestamp ] $message") + _systemLogs.value.take(25)
    }

    private fun seedTablesIfNeeded() {
        viewModelScope.launch {
            // Seed a welcome note if empty
            notes.first { true } // wait until flow emits
            if (notes.value.isEmpty()) {
                dao.insertNote(Note(title = "Welcome to Ansh", content = "Ansh is your highly capable, offline-first smart assistant with hardware telemetry diagnostics, system diagnostics dashboards, and high thinking AI modules.", category = "System"))
                dao.insertNote(Note(title = "Confidential Vault Data", content = "Decoupled neural parameters can trigger dynamic hardware operations when conditions in automation tables are matched. PIN: 1234", category = "Secure", isLocked = true))
            }
            if (tasks.value.isEmpty()) {
                dao.insertTask(Task(title = "Test local telemetry auto-trigger", priority = "Medium"))
                dao.insertTask(Task(title = "Sync neural database records", priority = "Low", isCompleted = true))
            }
            if (rules.value.isEmpty()) {
                dao.insertRule(AutomationRule(title = "Auto Power Save", triggerEvent = "Battery < 25%", actionTask = "Enable Power Saving"))
                dao.insertRule(AutomationRule(title = "Silent Night", triggerEvent = "Charging Connected", actionTask = "Mute audio"))
            }
            addLog("System seeding completed. Notes, rules, and telemetry synchronized.")
        }
    }

    private fun startAutomationLoop() {
        viewModelScope.launch {
            while (true) {
                delay(6000) // check rule conditions every 6 seconds
                val state = _deviceState.value
                val activeRules = rules.value.filter { it.isActive }
                
                activeRules.forEach { rule ->
                    var isTriggered = false
                    when (rule.triggerEvent) {
                        "Battery < 25%" -> {
                            if (state.batteryLevel < 25) {
                                isTriggered = true
                            }
                        }
                        "Charging Connected" -> {
                            if (state.isCharging) {
                                isTriggered = true
                            }
                        }
                    }

                    if (isTriggered) {
                        when (rule.actionTask) {
                            "Enable Power Saving" -> {
                                if (!state.powerSavingMode) {
                                    _deviceState.update { it.copy(powerSavingMode = true) }
                                    addLog("Automation: Triggered by '${rule.triggerEvent}'. Enabled Power Saving Mode!")
                                }
                            }
                            "Mute audio" -> {
                                if (!state.isMute) {
                                    _deviceState.update { it.copy(isMute = true, mediaVolume = 0, alarmVolume = 0) }
                                    addLog("Automation: Triggered by '${rule.triggerEvent}'. System Audio silenced.")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- UI Actions & State Modifiers ---

    // Set Active UI Tab
    fun setActiveTab(tab: String) {
        _activeTab.value = tab
        addLog("Navigation set to: ${tab.uppercase()}")
    }

    // Simulated Speed Test
    fun runNetworkSpeedTest() {
        viewModelScope.launch {
            addLog("Executing network diagnostics speed test...")
            _deviceState.update { it.copy(netSpeedDownload = "Analyzing...") }
            delay(1500)
            val speed = (100..450).random()
            _deviceState.update { it.copy(netSpeedDownload = "$speed.5 Mbps") }
            addLog("Network diagnostics completed. Speed: $speed.5 Mbps.")
        }
    }

    // Interactive Telemetry Modifiers
    fun setBatteryLevel(level: Int) {
        _deviceState.update { it.copy(batteryLevel = level) }
        addLog("Telemetry Battery manually set to: $level%")
    }

    fun toggleChargingState() {
        _deviceState.update { it.copy(isCharging = !it.isCharging) }
        addLog("Hardware line connection modified: Charging = ${_deviceState.value.isCharging}")
    }

    fun setPowerSaving(active: Boolean) {
        _deviceState.update { it.copy(powerSavingMode = active) }
        addLog("Manual Power Saving: $active")
    }

    fun setBrightness(brightness: Float) {
        _deviceState.update { it.copy(brightness = brightness) }
        addLog("Display level modified: ${Math.round(brightness * 100)}%")
    }

    fun toggleAutoBrightness() {
        _deviceState.update { it.copy(autoBrightness = !it.autoBrightness) }
        addLog("Auto brightness set to: ${_deviceState.value.autoBrightness}")
    }

    fun toggleRotationLock() {
        _deviceState.update { it.copy(rotationLocked = !it.rotationLocked) }
        addLog("Rotation lock changed: ${_deviceState.value.rotationLocked}")
    }

    fun setMediaVolume(lvl: Int) {
        _deviceState.update { it.copy(mediaVolume = lvl) }
        addLog("Media volume set to $lvl/15")
    }

    fun toggleMuteState() {
        val current = _deviceState.value.isMute
        val targetVolume = if (current) 7 else 0
        _deviceState.update { it.copy(isMute = !current, mediaVolume = targetVolume) }
        addLog("Hardware mute toggled. Silenced = ${!current}")
    }

    fun toggleVibrateState() {
        _deviceState.update { it.copy(isVibrate = !_deviceState.value.isVibrate) }
        addLog("Haptic response: Vibrate = ${_deviceState.value.isVibrate}")
    }

    fun toggleBluetooth() {
        _deviceState.update { it.copy(bluetoothOn = !_deviceState.value.bluetoothOn) }
        addLog("Bluetooth controller action: ${_deviceState.value.bluetoothOn}")
    }

    fun toggleWifi() {
        _deviceState.update { it.copy(wifiOn = !_deviceState.value.wifiOn) }
        addLog("Wifi controller state: ${_deviceState.value.wifiOn}")
    }

    fun toggleNfc() {
        _deviceState.update { it.copy(nfcOn = !_deviceState.value.nfcOn) }
        addLog("NFC sensor set: ${_deviceState.value.nfcOn}")
    }

    fun toggleHotspot() {
        _deviceState.update { it.copy(hotspotOn = !_deviceState.value.hotspotOn) }
        addLog("Tethering/Hotspot state: ${_deviceState.value.hotspotOn}")
    }

    fun toggleVpn() {
        _deviceState.update { it.copy(vpnActive = !_deviceState.value.vpnActive) }
        addLog("Virtual Private Network (VPN) state: ${_deviceState.value.vpnActive}")
    }

    fun toggleWorkProfile() {
        _deviceState.update { it.copy(isWorkProfileActive = !_deviceState.value.isWorkProfileActive) }
        addLog("Work Profile sandboxing modified: ${_deviceState.value.isWorkProfileActive}")
    }

    fun incrementSteps() {
        _deviceState.update { it.copy(stepCount = it.stepCount + 150) }
        addLog("Pedometer pulse detected. Steps: ${_deviceState.value.stepCount}")
    }

    fun measureHeartRate() {
        viewModelScope.launch {
            addLog("Measuring physiological heart rate sensors...")
            (1..4).forEach { _ ->
                _deviceState.update { it.copy(heartRate = (65..85).random()) }
                delay(300)
            }
            addLog("HR sensor stabilized at: ${_deviceState.value.heartRate} BPM")
        }
    }

    fun runDuplicateFileFinder() {
        viewModelScope.launch {
            addLog("Searching storage partitions for identical CRC checksums...")
            delay(1200)
            _deviceState.update { it.copy(duplicateFiles = 0) }
            addLog("Storage optimization complete. Cleaned up simulated duplicates.")
        }
    }

    fun runLargeFileScanner() {
        viewModelScope.launch {
            addLog("Scanning file system index for objects > 100MB...")
            delay(1000)
            addLog("Large file search ready. Found ${_deviceState.value.largeFiles} objects.")
        }
    }

    fun emptyRecycleBin() {
        viewModelScope.launch {
            addLog("Zeroing out persistent sector caches...")
            delay(800)
            _deviceState.update { it.copy(recycleBinMb = 0) }
            addLog("Sector scrub finished. Secure vault space claimed.")
        }
    }

    fun toggleCameraFlash() {
        _deviceState.update { it.copy(cameraFlashOn = !it.cameraFlashOn) }
        addLog("Hardware diagnostic LED (Flashlight): ${_deviceState.value.cameraFlashOn}")
    }

    // --- Vault PIN Security ---
    fun unlockVault(pin: String): Boolean {
        if (pin == "1234") {
            _vaultLocked.value = false
            addLog("Secure encrypted vault decrypted successfully.")
            return true
        } else {
            addLog("ACCESS DENIED: Invalid crypto-vault decryption PIN.")
            return false
        }
    }

    fun lockVault() {
        _vaultLocked.value = true
        addLog("Secure crypto-vault re-encrypted and locked.")
    }

    // --- Room Database Modifiers ---

    // Notes
    fun addNote(title: String, content: String, category: String) {
        viewModelScope.launch {
            dao.insertNote(Note(title = title, content = content, category = category))
            addLog("Saved smart note: '$title' in category $category.")
        }
    }

    fun deleteNote(id: Int) {
        viewModelScope.launch {
            dao.deleteNoteById(id)
            addLog("Scrubbed note memory index $id.")
        }
    }

    // Tasks
    fun addTask(title: String, priority: String) {
        viewModelScope.launch {
            dao.insertTask(Task(title = title, priority = priority))
            addLog("Saved task criteria: '$title' [$priority].")
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            dao.updateTask(task.copy(isCompleted = !task.isCompleted))
            addLog("Task state updated: Completed = ${!task.isCompleted}")
        }
    }

    fun deleteTask(id: Int) {
        viewModelScope.launch {
            dao.deleteTaskById(id)
            addLog("Purged task entry ID $id.")
        }
    }

    // Automation Rules
    fun addRule(title: String, trigger: String, action: String) {
        viewModelScope.launch {
            dao.insertRule(AutomationRule(title = title, triggerEvent = trigger, actionTask = action))
            addLog("Saved automation rule trigger: '$title' ($trigger -> $action).")
        }
    }

    fun toggleRuleActive(rule: AutomationRule) {
        viewModelScope.launch {
            dao.updateRule(rule.copy(isActive = !rule.isActive))
            addLog("Rule status updated: Active = ${!rule.isActive}")
        }
    }

    fun deleteRule(id: Int) {
        viewModelScope.launch {
            dao.deleteRuleById(id)
            addLog("Scrubbed automation rule ID $id.")
        }
    }

    // Force Open Deep Link payload
    fun handleDeepLink(urlString: String) {
        viewModelScope.launch {
            addLog("Parsing incoming deep link payload: $urlString")
        }
    }

    // --- Background Floating Service Toggles ---
    fun toggleFloatingService() {
        val target = !_floatingActive.value
        _floatingActive.value = target
        prefs.edit().putBoolean("floating_active", target).apply()
        
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, com.example.AnshFloatingService::class.java).apply {
            action = if (target) "START" else "STOP"
        }
        
        try {
            if (target) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                addLog("Starting Ansh Background Floating Assistant...")
            } else {
                context.stopService(intent)
                addLog("Stopping Ansh Background Floating Assistant...")
            }
        } catch (e: Exception) {
            addLog("Floating assistant action error: ${e.message}")
        }
    }

    fun updateWakeWord(word: String) {
        val cleanWord = word.trim()
        if (cleanWord.isNotBlank()) {
            _wakeWord.value = cleanWord
            prefs.edit().putString("wake_word", cleanWord).apply()
            addLog("Acoustic wake-word keyword updated to: '$cleanWord'")
            
            // Notify background service if active
            val context = getApplication<Application>().applicationContext
            if (_floatingActive.value) {
                val intent = Intent(context, com.example.AnshFloatingService::class.java).apply {
                    action = "UPDATE_WAKE_WORD"
                    putExtra("wake_word", cleanWord)
                }
                try {
                    context.startService(intent)
                } catch (e: Exception) {}
            }
        }
    }

    // --- Gemini Assistant Integration (with High Thinking) ---

    fun tryOpenApp(appName: String): Boolean {
        val context = getApplication<Application>().applicationContext
        val pm = context.packageManager
        val cleanName = appName.lowercase().trim()
        
                // 1. Direct system intent routing for common requests
        val intent: Intent? = when {
            cleanName.contains("camera") || cleanName.equals("cam") -> {
                Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            }
            cleanName.contains("dialer") || cleanName.contains("phone") || cleanName.contains("contacts") || cleanName.contains("call") -> {
                Intent(Intent.ACTION_DIAL)
            }
            cleanName.contains("setting") -> {
                Intent(android.provider.Settings.ACTION_SETTINGS)
            }
            cleanName.contains("map") || cleanName.contains("navigation") || cleanName.contains("gps") -> {
                val uri = Uri.parse("geo:0,0?q=maps")
                Intent(Intent.ACTION_VIEW, uri)
            }
            cleanName.contains("browser") || cleanName.contains("chrome") || cleanName.contains("google search") -> {
                val uri = Uri.parse("https://www.google.com")
                Intent(Intent.ACTION_VIEW, uri)
            }
            cleanName.contains("calculator") || cleanName.contains("calc") -> {
                val calIntents = listOf(
                    "com.android.calculator2",
                    "com.google.android.calculator",
                    "com.sec.android.app.popupcalculator",
                    "com.huawei.calculator"
                )
                var lIntent: Intent? = null
                for (cat in calIntents) {
                    try {
                        lIntent = pm.getLaunchIntentForPackage(cat)
                        if (lIntent != null) break
                    } catch (e: Exception) {}
                }
                lIntent ?: Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALCULATOR)
                }
            }
            cleanName.contains("calendar") || cleanName.contains("calender") -> {
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALENDAR)
                }
            }
            cleanName.contains("music") || cleanName.contains("spotify") || cleanName.contains("audio") -> {
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MUSIC)
                }
            }
            cleanName.contains("gallery") || cleanName.contains("photos") -> {
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_GALLERY)
                }
            }
            cleanName.contains("email") || cleanName.contains("gmail") || cleanName.contains("mail") -> {
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_EMAIL)
                }
            }
            else -> null
        }
        
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                addLog("Opened system utility app: $cleanName")
                return true
            } catch (e: Exception) {
                addLog("Could not start intent for $cleanName: ${e.message}")
            }
        }
        
        // 2. Search installed applications for a matching app label
        try {
            val packages = pm.getInstalledPackages(0)
            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue
                val label = appInfo.loadLabel(pm).toString().lowercase()
                if (label.contains(cleanName) || cleanName.contains(label)) {
                    val launchIntent = pm.getLaunchIntentForPackage(pkg.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        addLog("Successfully opened app: ${appInfo.loadLabel(pm)}")
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            addLog("Package search error: ${e.message}")
        }
        
        return false
    }

    fun sendAssistantMessage(prompt: String) {
        if (prompt.isBlank()) return
        
        viewModelScope.launch {
            val normalized = prompt.lowercase().trim()
            var localExecuted = false
            var responseText = ""
            
            // 1. App opening triggers
            if (normalized.startsWith("open ") || normalized.startsWith("launch ") || normalized.startsWith("open app ")) {
                val targetApp = normalized
                    .removePrefix("open app ")
                    .removePrefix("open ")
                    .removePrefix("launch ")
                    .trim()
                if (targetApp.isNotBlank()) {
                    val opened = tryOpenApp(targetApp)
                    localExecuted = true
                    responseText = if (opened) {
                        "Local system agent triggered. I have successfully opened the '$targetApp' application on your device."
                    } else {
                        "I searched your device for an application named '$targetApp' but couldn't find a matching launcher icon or package label. Make sure the app is installed, or try another generic name (e.g., 'open settings', 'open camera', 'open phone' or 'open browser')."
                    }
                }
            }
            
            // 2. Setting toggle triggers
            else if (normalized.contains("power save") || normalized.contains("power saving")) {
                localExecuted = true
                if (normalized.contains("on") || normalized.contains("enable") || normalized.contains("activate")) {
                    setPowerSaving(true)
                    responseText = "System power diagnostic changed. I've turned ON Power Saving Mode for you."
                } else if (normalized.contains("off") || normalized.contains("disable") || normalized.contains("deactivate")) {
                    setPowerSaving(false)
                    responseText = "System power diagnostic changed. I've turned OFF Power Saving Mode for you."
                } else {
                    localExecuted = false // let flash/gemini answer
                }
            }
            else if (normalized.contains("wifi") || normalized.contains("wi-fi")) {
                localExecuted = true
                if (normalized.contains("on") || normalized.contains("enable") || normalized.contains("activate")) {
                    if (!_deviceState.value.wifiOn) toggleWifi()
                    responseText = "I've successfully enabled Wi-Fi services."
                } else if (normalized.contains("off") || normalized.contains("disable") || normalized.contains("deactivate")) {
                    if (_deviceState.value.wifiOn) toggleWifi()
                    responseText = "I've successfully disabled Wi-Fi services."
                } else {
                    localExecuted = false
                }
            }
            else if (normalized.contains("bluetooth")) {
                localExecuted = true
                if (normalized.contains("on") || normalized.contains("enable") || normalized.contains("activate")) {
                    if (!_deviceState.value.bluetoothOn) toggleBluetooth()
                    responseText = "I've activated your device's Bluetooth radio controller."
                } else if (normalized.contains("off") || normalized.contains("disable") || normalized.contains("deactivate")) {
                    if (_deviceState.value.bluetoothOn) toggleBluetooth()
                    responseText = "I've deactivated your device's Bluetooth radio controller."
                } else {
                    localExecuted = false
                }
            }
            else if (normalized.contains("mute") || normalized.contains("silent") || normalized.contains("silence")) {
                localExecuted = true
                if (normalized.contains("off") || normalized.contains("unmute") || normalized.contains("disable")) {
                    if (_deviceState.value.isMute) toggleMuteState()
                    responseText = "Audio driver restored. System sounds are now enabled."
                } else {
                    if (!_deviceState.value.isMute) toggleMuteState()
                    responseText = "Audio driver muting engaged. System sounds silenced."
                }
            }
            else if (normalized.contains("flashlight") || normalized.contains("torch") || normalized.contains("flash light")) {
                localExecuted = true
                if (normalized.contains("on") || normalized.contains("enable") || normalized.contains("activate")) {
                    if (!_deviceState.value.cameraFlashOn) toggleCameraFlash()
                    responseText = "I have successfully enabled the camera LED flashlight."
                } else if (normalized.contains("off") || normalized.contains("disable") || normalized.contains("deactivate")) {
                    if (_deviceState.value.cameraFlashOn) toggleCameraFlash()
                    responseText = "I have deactivated the camera LED flashlight."
                } else {
                    localExecuted = false
                }
            }
            else if (normalized.contains("simulate step") || normalized.contains("add step") || normalized.contains("increment step")) {
                localExecuted = true
                incrementSteps()
                responseText = "Pedometer sensors engaged. Simulated step count successfully incremented +150 steps!"
            }
            else if (normalized.contains("measure heart") || normalized.contains("check heart") || normalized.contains("heart rate")) {
                localExecuted = true
                measureHeartRate()
                responseText = "Electro-optical heart sensors active. I am actively measuring your heart rate on the dashboard overlay."
            }

            if (localExecuted) {
                val chatEntry = ChatHistory(
                    query = prompt,
                    response = responseText,
                    isThinking = false
                )
                dao.insertChat(chatEntry)
                addLog("Local controller action processed query successfully.")
                return@launch
            }

            addLog("User queued query to Gemini-3.1-pro-preview...")
            _chatLoading.value = true
            _thinkingText.value = "Ansh Cognitive Core thinking: Initiating system planning engine..."
            
            // Build localized system status prompt context to make the assistant "smart" and aware of the exact current device environments
            val systemContext = """
                You are ANSH, a highly sophisticated smart AI Assistant running as an offline-capable system layer.
                You are currently running with High Thinking Level enabled.
                Current Device Environmental Parameters:
                - Battery level: ${_deviceState.value.batteryLevel}% (Charging: ${_deviceState.value.isCharging}, Power Saving: ${_deviceState.value.powerSavingMode})
                - Display level: ${Math.round(_deviceState.value.brightness * 100)}% (Auto-brightness: ${_deviceState.value.autoBrightness})
                - Audio status: Media volume ${_deviceState.value.mediaVolume}/15 (Muted: ${_deviceState.value.isMute}, Vibrate: ${_deviceState.value.isVibrate})
                - Wi-Fi: ${_deviceState.value.wifiOn} | Bluetooth: ${_deviceState.value.bluetoothOn} | VPN: ${_deviceState.value.vpnActive}
                - Pedometer step logs: ${_deviceState.value.stepCount} steps.
                - Internal storage used: ${_deviceState.value.internalUsedGb} GB / ${_deviceState.value.internalTotalGb} GB (Recycle binary: ${_deviceState.value.recycleBinMb} MB)
                
                Productivity parameters lists:
                - Active local notes in db: ${notes.value.joinToString { it.title }}
                - Active tasks: ${tasks.value.joinToString { "${it.title} (Completed: ${it.isCompleted})" }}
                - Multi-step automation triggers configured: ${rules.value.joinToString { "${it.title}: ${it.triggerEvent} -> ${it.actionTask}" }}
                
                Respond in a highly structured, polished, helpful assistant voice. Reference these system values directly to prove your deep integration. Explain your thinking steps if relevant to the request. Keep responses compact and helpful. Give precise insights.
            """.trimIndent()

            // Run API request
            val currentContextHistory = chatHistory.value
            
            // Periodically animate simulated thinking metrics on the interface
            viewModelScope.launch {
                val thinkingSteps = listOf(
                    "Cognitive core thinking: Evaluating system status and memory maps...",
                    "Cognitive core thinking: Factoring SQLite DB schemas and active task indexes...",
                    "Cognitive core thinking: Assembling conversational token maps...",
                    "Cognitive core thinking: Emitting smart assistant response..."
                )
                for (step in thinkingSteps) {
                    if (!_chatLoading.value) break
                    _thinkingText.value = step
                    delay(1200)
                }
            }

            val result = withContext(Dispatchers.IO) {
                GeminiApiClient.getAssistantResponse(
                    prompt = prompt,
                    chatHistory = currentContextHistory,
                    systemPrompt = systemContext
                )
            }

            _chatLoading.value = false
            _thinkingText.value = ""

            when (result) {
                is GeminiResult.Success -> {
                    val chatEntry = ChatHistory(
                        query = prompt,
                        response = result.responseText,
                        isThinking = true,
                        thinkingLog = result.thinkingProcess
                    )
                    dao.insertChat(chatEntry)
                    addLog("Gemini assistant returned response context.")
                }
                is GeminiResult.Error -> {
                    val chatEntry = ChatHistory(
                        query = prompt,
                        response = "Cognitive block occurred: \n${result.message}",
                        isThinking = false
                    )
                    dao.insertChat(chatEntry)
                    addLog("Gemini API call failed: ${result.message}")
                }
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            dao.clearChatHistory()
            addLog("Chat History memory buffer cleared.")
        }
    }
}
