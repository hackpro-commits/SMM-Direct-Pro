package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AuthScreen(
    onLoginSuccess: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var verificationSentMessage by remember { mutableStateOf<String?>(null) }
    var awaitingVerificationUser by remember { mutableStateOf<com.google.firebase.auth.FirebaseUser?>(null) }

    // Check if user is already logged in but unverified
    LaunchedEffect(Unit) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            currentUser.reload().await()
            if (currentUser.isEmailVerified) {
                onLoginSuccess()
            } else {
                awaitingVerificationUser = currentUser
            }
        }
    }

    if (awaitingVerificationUser != null) {
        // Verification Pending Screen
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.Email, contentDescription = "Email", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                    Text(
                        text = "Verify Your Gmail Address",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "We have sent a verification link to:\n${awaitingVerificationUser?.email}\n\nPlease click the link in your email before proceeding.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (verificationSentMessage != null) {
                        Text(text = verificationSentMessage!!, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = {
                            isLoading = true
                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    awaitingVerificationUser?.reload()?.await()
                                    if (awaitingVerificationUser?.isEmailVerified == true) {
                                        onLoginSuccess()
                                    } else {
                                        errorMessage = "Email not verified yet. Please check your inbox."
                                    }
                                } catch (e: Exception) {
                                    errorMessage = e.localizedMessage
                                }
                                isLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                        } else {
                            Text("I've Verified My Email", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }

                    TextButton(onClick = {
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                awaitingVerificationUser?.sendEmailVerification()?.await()
                                verificationSentMessage = "Verification email resent successfully!"
                            } catch (e: Exception) {
                                errorMessage = e.localizedMessage
                            }
                        }
                    }) {
                        Text("Resend Verification Email", color = MaterialTheme.colorScheme.primary)
                    }

                    TextButton(onClick = {
                        auth.signOut()
                        awaitingVerificationUser = null
                    }) {
                        Text("Sign Out / Use Another Account", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    } else {
        // Sign In / Sign Up Screen
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (isSignUp) "Create SMM Account" else "SMM Direct Pro Login",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Only @gmail.com email addresses are allowed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; errorMessage = null },
                        label = { Text("Gmail Address (@gmail.com)") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Email, contentDescription = "Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        label = { Text("Password") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Lock, contentDescription = "Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (errorMessage != null) {
                        Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = {
                            val trimmedEmail = email.trim()
                            if (!trimmedEmail.endsWith("@gmail.com", ignoreCase = true)) {
                                errorMessage = "Only @gmail.com email addresses are allowed!"
                                return@Button
                            }
                            if (password.length < 6) {
                                errorMessage = "Password must be at least 6 characters"
                                return@Button
                            }

                            isLoading = true
                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    if (isSignUp) {
                                        val result = auth.createUserWithEmailAndPassword(trimmedEmail, password).await()
                                        val user = result.user
                                        if (user != null) {
                                            user.sendEmailVerification().await()
                                            awaitingVerificationUser = user
                                            verificationSentMessage = "Verification link sent to $trimmedEmail"
                                        }
                                    } else {
                                        val result = auth.signInWithEmailAndPassword(trimmedEmail, password).await()
                                        val user = result.user
                                        if (user != null) {
                                            user.reload().await()
                                            if (user.isEmailVerified) {
                                                onLoginSuccess()
                                            } else {
                                                awaitingVerificationUser = user
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    errorMessage = e.localizedMessage ?: "Authentication failed"
                                }
                                isLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                        } else {
                            Text(if (isSignUp) "Register with Gmail" else "Sign In", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    }

                    TextButton(onClick = { isSignUp = !isSignUp; errorMessage = null }) {
                        Text(
                            text = if (isSignUp) "Already have an account? Sign In" else "Don't have an account? Sign Up",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
