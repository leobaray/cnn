package com.lbwma.cnn.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.lbwma.cnn.BiometricHelper
import com.lbwma.cnn.network.ApiClient
import com.lbwma.cnn.ui.theme.Cyan40
import com.lbwma.cnn.ui.theme.Dark00
import com.lbwma.cnn.ui.theme.Dark10
import com.lbwma.cnn.ui.theme.Dark15
import com.lbwma.cnn.ui.theme.Dark20
import com.lbwma.cnn.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, sessionExpired: Boolean = false) {
    val context = LocalContext.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showExpiredNotice by remember { mutableStateOf(sessionExpired) }
    var contentVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val biometricAvailable = remember {
        BiometricHelper.isBiometricAvailable(context) && BiometricHelper.hasSavedCredentials(context)
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Cyan40,
        unfocusedBorderColor = Dark15,
        focusedLabelColor = Cyan40,
        cursorColor = Cyan40,
    )

    fun doBiometricLogin() {
        val executor = ContextCompat.getMainExecutor(context)
        BiometricHelper.authenticate(
            context = context,
            executor = executor,
            onSuccess = {
                val creds = BiometricHelper.getSavedCredentials(context)
                if (creds != null) {
                    loading = true
                    error = null
                    showExpiredNotice = false
                    ApiClient.configure(creds.first, creds.second)
                    scope.launch {
                        when (ApiClient.testConnection()) {
                            ApiClient.LoginResult.Ok -> onLoginSuccess()
                            ApiClient.LoginResult.Unauthorized -> {
                                loading = false
                                BiometricHelper.clearCredentials(context)
                                error = "Credenciais salvas expiradas. Faça login novamente."
                            }
                            ApiClient.LoginResult.NetworkError -> {
                                loading = false
                                error = "Sem conexão com o servidor"
                            }
                        }
                    }
                }
            },
            onDismiss = { }
        )
    }

    fun doLogin() {
        if (loading || username.isBlank() || password.isBlank()) return
        loading = true
        error = null
        showExpiredNotice = false
        focusManager.clearFocus()
        ApiClient.configure(username.trim(), password)
        scope.launch {
            when (ApiClient.testConnection()) {
                ApiClient.LoginResult.Ok -> {
                    if (BiometricHelper.isBiometricAvailable(context)) {
                        BiometricHelper.saveCredentials(context, username.trim(), password)
                    }
                    onLoginSuccess()
                }
                ApiClient.LoginResult.Unauthorized -> { loading = false; error = "Usuário ou senha incorretos" }
                ApiClient.LoginResult.NetworkError -> { loading = false; error = "Sem conexão com o servidor" }
            }
        }
    }

    LaunchedEffect(Unit) {
        contentVisible = true
        if (biometricAvailable && !sessionExpired) {
            delay(400)
            doBiometricLogin()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF060D14), Dark00, Dark00)
                )
            )
    ) {
        // Ambient glow at top
        Box(
            modifier = Modifier
                .size(480.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Cyan40.copy(alpha = 0.06f), Color.Transparent)
                    )
                )
        )

        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(tween(700)) + slideInVertically(tween(800)) { it / 6 },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Branding with radial glow
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Cyan40.copy(alpha = 0.18f), Color.Transparent)
                                )
                            )
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "CNN",
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Black,
                            color = Cyan40,
                            letterSpacing = 6.sp
                        )
                        Text(
                            "CONVERSORES",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary,
                            letterSpacing = 4.sp
                        )
                    }
                }

                Spacer(Modifier.height(48.dp))

                // Form card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, Cyan40.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .background(Dark10)
                        .padding(24.dp)
                ) {
                    Text(
                        "Entrar",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    AnimatedVisibility(
                        visible = showExpiredNotice,
                        enter = fadeIn() + slideInVertically()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Dark20)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                "Sessão encerrada por inatividade",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it; error = null; showExpiredNotice = false },
                        label = { Text("Usuário") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )
                    Spacer(Modifier.height(14.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text("Senha") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { doLogin() })
                    )

                    AnimatedVisibility(
                        visible = error != null,
                        enter = fadeIn() + slideInVertically()
                    ) {
                        Text(
                            error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = { doLogin() },
                        enabled = !loading && username.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Cyan40,
                            disabledContainerColor = Dark15
                        )
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                "ENTRAR",
                                style = MaterialTheme.typography.labelLarge,
                                letterSpacing = 2.sp
                            )
                        }
                    }

                    if (biometricAvailable) {
                        Spacer(Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = { doBiometricLogin() },
                            enabled = !loading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Cyan40.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Cyan40
                            )
                        ) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = "Digital",
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "ENTRAR COM DIGITAL",
                                style = MaterialTheme.typography.labelLarge,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
