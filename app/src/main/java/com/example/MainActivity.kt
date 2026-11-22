package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.example.data.repository.SmmRepository
import com.example.ui.SmmApp
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.SmmDirectProTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val repository = SmmRepository(applicationContext)
    setContent {
      SmmDirectProTheme {
        var isAuthenticated by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.isEmailVerified == true) }

        if (isAuthenticated) {
          SmmApp(repository = repository)
        } else {
          AuthScreen(
            onLoginSuccess = { isAuthenticated = true }
          )
        }
      }
    }
  }
}
