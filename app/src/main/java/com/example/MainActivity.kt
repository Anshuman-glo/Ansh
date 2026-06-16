package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.AnshDashboard
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AnshViewModel

class MainActivity : ComponentActivity() {

  private val viewModel: AnshViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Capture incoming deep-links
    handleIncomingIntent(intent)

    setContent {
      MyApplicationTheme {
        AnshDashboard(viewModel = viewModel)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIncomingIntent(intent)
  }

  private fun handleIncomingIntent(intent: Intent?) {
    if (intent != null && intent.action == Intent.ACTION_VIEW) {
      val dataUri = intent.data
      if (dataUri != null) {
        val urlText = dataUri.toString()
        viewModel.handleDeepLink(urlText)
      }
    }
  }
}
