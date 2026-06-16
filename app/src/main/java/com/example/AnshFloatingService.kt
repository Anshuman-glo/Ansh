package com.example

import androidx.compose.ui.draw.scale
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.savedstate.SavedStateRegistryOwner
import com.example.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import java.util.Locale

// Structure for local overlay messages
data class OverlayChat(
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class OverlayState(
    val isExpanded: Boolean = false,
    val statusText: String = "IDLE (Listening...)",
    val wakeWord: String = "Ansh",
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val chats: List<OverlayChat> = listOf(
        OverlayChat("Ansh", "Secure background listener online. Try saying: 'Ansh, check my heart rate' or 'Ansh, toggle power saving state'!")
    )
)

class AnshFloatingService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var baseContainer: FrameLayout? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _uiState = MutableStateFlow(OverlayState())
    val uiState = _uiState.asStateFlow()

    private val prefs by lazy {
        getSharedPreferences("ansh_prefs", Context.MODE_PRIVATE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        _uiState.update {
            it.copy(wakeWord = prefs.getString("wake_word", "Ansh") ?: "Ansh")
        }

        // Setup notification foreground to prevent kill
        startNotificationForeground()

        // Setup TextToSpeech
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                isTtsReady = true
            }
        }

        // Initialize Floating View & Speech loop
        setupFloatingView()
        startSpeechRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        } else if (action == "UPDATE_WAKE_WORD") {
            val newWake = intent.getStringExtra("wake_word") ?: "Ansh"
            _uiState.update { it.copy(wakeWord = newWake) }
        }
        return START_STICKY
    }

    private fun setupFloatingView() {
        val context = this
        baseContainer = FrameLayout(context)

        // Custom lifecycle scopes setup for window view
        baseContainer?.setViewTreeLifecycleOwner(this)
        baseContainer?.setViewTreeViewModelStoreOwner(this)
        baseContainer?.setViewTreeSavedStateRegistryOwner(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 350
        }

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by uiState.collectAsState()
                
                Box(modifier = Modifier.wrapContentSize()) {
                    if (state.isExpanded) {
                        ExpandedDashboardView(
                            state = state,
                            onCollapse = { _uiState.update { it.copy(isExpanded = false) } },
                            onSendText = { processAssistantQuery(it) },
                            onShutdown = { stopSelf() }
                        )
                    } else {
                        // Glowing drag orb
                        DragOrbView(
                            state = state,
                            onTouchEvent = { event ->
                                handleDragEvent(event, params)
                            }
                        )
                    }
                }
            }
        }

        baseContainer?.addView(composeView)
        try {
            windowManager.addView(baseContainer, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private fun handleDragEvent(event: MotionEvent, params: WindowManager.LayoutParams): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                    isDragging = true
                }
                if (isDragging) {
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    try {
                        windowManager.updateViewLayout(baseContainer, params)
                    } catch (e: Exception) {}
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    // It was a click! Toggle expand
                    _uiState.update { it.copy(isExpanded = !it.isExpanded) }
                }
                return true
            }
        }
        return false
    }

    @Composable
    fun DragOrbView(state: OverlayState, onTouchEvent: (MotionEvent) -> Boolean) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )

        val borderGlow = Brush.linearGradient(
            colors = if (state.isProcessing) {
                listOf(Color(0xFF00FFCC), Color(0xFF9900FF))
            } else if (state.isListening) {
                listOf(Color(0xFF7F3DEC), Color(0xFF00B2FF))
            } else {
                listOf(Color(0xFF7F3DEC).copy(alpha = 0.6f), Color(0xFF6B7280).copy(alpha = 0.6f))
            }
        )

        Card(
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0D15).copy(alpha = 0.9f)),
            border = BorderStroke(2.dp, borderGlow),
            modifier = Modifier
                .size(62.dp)
                .scale(scale)
                .pointerInput(Unit) {
                    // Directly handle raw events via listener
                },
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { _uiState.update { it.copy(isExpanded = true) } },
                contentAlignment = Alignment.Center
            ) {
                // Background ripple touch handling intercept
                AndroidTouchView { onTouchEvent(it) }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (state.isProcessing) Icons.Default.Cyclone else Icons.Default.Hearing,
                        contentDescription = "Ansh Companion",
                        tint = if (state.isListening) Color(0xFF00FFCC) else Color(0xFFBD9BFF),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = state.wakeWord.uppercase(),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }

    @Composable
    fun ExpandedDashboardView(
        state: OverlayState,
        onCollapse: () -> Unit,
        onSendText: (String) -> Unit,
        onShutdown: () -> Unit
    ) {
        val listState = rememberLazyListState()
        var textInput by remember { mutableStateOf("") }
        
        LaunchedEffect(state.chats.size) {
            if (state.chats.isNotEmpty()) {
                listState.animateScrollToItem(state.chats.size - 1)
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0B16)),
            border = BorderStroke(1.dp, Color(0xFF7F3DEC).copy(alpha = 0.5f)),
            modifier = Modifier
                .width(310.dp)
                .height(380.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E152F))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = Color(0xFF00FFCC),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ANSH COGNITIVE PORTAL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE2D6FF),
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onCollapse, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.Remove, contentDescription = "Collapse", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onShutdown, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = "Exit", tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Chat conversations list
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.chats) { msg ->
                        val isUser = msg.sender == "You"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isUser) Color(0xFF3B1B6D) else Color(0xFF181524)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isUser) Color(0xFF7F3DEC).copy(alpha = 0.5f) else Color(0x33FFFFFF),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(8.dp)
                                    .widthIn(max = 220.dp)
                            ) {
                                Column {
                                    Text(
                                        text = if (isUser) "YOU" else "ANSH",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isUser) Color(0xFF00FFCC) else Color(0xFFB485FF),
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                    Text(
                                        text = msg.text,
                                        fontSize = 11.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // Processing indicators
                if (state.isProcessing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.dp, color = Color(0xFF00FFCC))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ansh thinking cores active...", fontSize = 10.sp, color = Color(0xFF00FFCC), fontFamily = FontFamily.Monospace)
                    }
                }

                // Keyboard entry line
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp),
                        placeholder = { Text("Manual direct override...", color = Color.Gray, fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (textInput.isNotBlank()) {
                                onSendText(textInput)
                                textInput = ""
                            }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF7F3DEC),
                            unfocusedBorderColor = Color(0xFF22183D)
                        ),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                onSendText(textInput)
                                textInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F3DEC)),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }

                // Footer diagnostics statusText
                Surface(
                    color = Color(0xFF06040A),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF22183D))
                        .padding(6.dp)
                ) {
                    Text(
                        text = state.statusText.uppercase(),
                        textAlign = TextAlign.Center,
                        fontSize = 8.sp,
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }

    // Interceptor layout inside Android framework Window
    @Composable
    fun AndroidTouchView(onTouchEvent: (MotionEvent) -> Boolean) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { context ->
                View(context).apply {
                    setOnTouchListener { _, event ->
                        onTouchEvent(event)
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    // --- Active Speech Listener loop ---
    private fun startSpeechRecognizer() {
        serviceScope.launch {
            try {
                speechRecognizer?.destroy()
                
                if (!SpeechRecognizer.isRecognitionAvailable(this@AnshFloatingService)) {
                    _uiState.update { it.copy(statusText = "Acoustic driver failed: No system speech client available.") }
                    return@launch
                }

                val recognizer = SpeechRecognizer.createSpeechRecognizer(this@AnshFloatingService, android.content.ComponentName.unflattenFromString("com.google.android.google/com.google.android.voiceinteraction.GsaVoiceInteractionService"))
                    ?: SpeechRecognizer.createSpeechRecognizer(this@AnshFloatingService)

                speechRecognizer = recognizer

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                }

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _uiState.update {
                            it.copy(
                                isListening = true,
                                statusText = "Active Companion Standing By (Say: '${it.wakeWord}')"
                            )
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        _uiState.update { it.copy(statusText = "Companion: Capturing conversation...") }
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _uiState.update { it.copy(statusText = "Companion: Decoding transcript...") }
                    }

                    override fun onError(error: Int) {
                        _uiState.update {
                            it.copy(
                                isListening = false,
                                statusText = "Continuous scan active (Standby)"
                            )
                        }
                        restartSpeechRecognizerWithDelay()
                    }

                    override fun onResults(results: Bundle?) {
                        _uiState.update { it.copy(isListening = false) }
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (matches != null && matches.isNotEmpty()) {
                            val candidate = matches[0] ?: ""
                            handleSpeechInput(candidate)
                        } else {
                            restartSpeechRecognizerWithDelay()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                recognizer.startListening(intent)
            } catch (e: Exception) {
                _uiState.update { it.copy(statusText = "Recognizer Error: ${e.message}") }
            }
        }
    }

    private fun restartSpeechRecognizerWithDelay() {
        serviceScope.launch {
            delay(1200)
            if (prefs.getBoolean("floating_active", false)) {
                startSpeechRecognizer()
            }
        }
    }

    private fun handleSpeechInput(rawText: String) {
        val word = _uiState.value.wakeWord.lowercase().trim()
        val text = rawText.trim()
        val lowercaseText = text.lowercase()

        if (lowercaseText.contains(word)) {
            val wordIndex = lowercaseText.indexOf(word)
            var command = text.substring(wordIndex + word.length).trim()

            // Remove leading garbage markers
            if (command.startsWith(",") || command.startsWith(":") || command.startsWith("-")) {
                command = command.substring(1).trim()
            }
            if (command.startsWith("hey") || command.startsWith("do") || command.startsWith("to")) {
                command = command.removePrefix("hey").removePrefix("do").removePrefix("to").trim()
            }

            if (command.isBlank()) {
                speakAndShowReply("Yes? Standing by, please speak your command.")
            } else {
                processAssistantQuery(command)
            }
        } else {
            // Keep looping background scanning
            restartSpeechRecognizerWithDelay()
        }
    }

    private fun speakAndShowReply(reply: String) {
        _uiState.update {
            it.copy(
                chats = it.chats + OverlayChat("Ansh", reply),
                isProcessing = false,
                statusText = "Idle"
            )
        }

        if (isTtsReady) {
            tts?.speak(reply, TextToSpeech.QUEUE_FLUSH, null, null)
        }
        restartSpeechRecognizerWithDelay()
    }

    private fun processAssistantQuery(query: String) {
        _uiState.update {
            it.copy(
                chats = it.chats + OverlayChat("You", query),
                isProcessing = true,
                statusText = "Brain processing..."
            )
        }

        serviceScope.launch {
            // Context assembly to match system features if needed
            val systemContext = "You are ANSH, a background floating companion. You are speaking with the user directly over floating vocal channels. Respond clearly and keep it extremely short, ideally under 2 sentences, because you will be read aloud."
            
            val result = withContext(Dispatchers.IO) {
                GeminiApiClient.getAssistantResponse(
                    prompt = query,
                    chatHistory = emptyList(),
                    systemPrompt = systemContext
                )
            }

            when (result) {
                is GeminiResult.Success -> {
                    speakAndShowReply(result.responseText)
                    
                    // Also save conversation to actual Room DB history
                    try {
                        val db = androidx.room.Room.databaseBuilder(
                            applicationContext,
                            AnshDatabase::class.java, "ansh-assistant-db"
                        ).build()
                        db.dao().insertChat(
                            ChatHistory(
                                query = "[VOCAL] $query",
                                response = result.responseText,
                                isThinking = false
                            )
                        )
                    } catch (e: Exception) {}
                }
                is GeminiResult.Error -> {
                    speakAndShowReply("Cognition fault: " + result.message)
                }
            }
        }
    }

    private fun startNotificationForeground() {
        val channelId = "ansh_assistant_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Ansh companion overlay channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ansh Active Companion Active")
            .setContentText("Continuous background wake-listening standing by.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1337, notification)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        
        serviceScope.cancel()
        
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {}

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {}

        if (baseContainer != null) {
            try {
                windowManager.removeView(baseContainer)
            } catch (e: Exception) {}
        }
        
        super.onDestroy()
    }
}
