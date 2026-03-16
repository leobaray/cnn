package com.lbwma.cnn.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
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
import com.lbwma.cnn.ui.theme.Cyan60
import com.lbwma.cnn.ui.theme.Dark00
import com.lbwma.cnn.ui.theme.Dark10
import com.lbwma.cnn.ui.theme.Dark15
import com.lbwma.cnn.ui.theme.Dark20
import com.lbwma.cnn.ui.theme.GlassBorder
import com.lbwma.cnn.ui.theme.GlassHighlight
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

    // Animated ambient glow
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.04f,
        targetValue = 0.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    val glowOffset by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowOffset"
    )

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Cyan40,
        unfocusedBorderColor = Dark20,
        focusedLabelColor = Cyan40,
        unfocusedLabelColor = TextSecondary,
        cursorColor = Cyan40,
        focusedLeadingIconColor = Cyan40,
        unfocusedLeadingIconColor = TextSecondary,
        focusedContainerColor = Dark15.copy(alpha = 0.5f),
        unfocusedContainerColor = Dark15.copy(alpha = 0.3f),
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
            .background(Dark00)
    ) {
        // Layered ambient glows
        Box(
            modifier = Modifier
                .size(500.dp)
                .offset(x = glowOffset.dp, y = (-60).dp)
                .align(Alignment.TopCenter)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Cyan40.copy(alpha = glowAlpha),
                                Cyan40.copy(alpha = glowAlpha * 0.3f),
                                Color.Transparent
                            )
                        ),
                        radius = size.minDimension / 2
                    )
                }
        )
        // Secondary warm glow
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-glowOffset * 0.7f).dp, y = 100.dp)
                .align(Alignment.TopEnd)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF0D47A1).copy(alpha = glowAlpha * 0.5f),
                                Color.Transparent
                            )
                        ),
                        radius = size.minDimension / 2
                    )
                }
        )

        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(tween(800)) + slideInVertically(tween(900, easing = FastOutSlowInEasing)) { it / 5 },
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
                // Branding
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .drawBehind {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Cyan40.copy(alpha = 0.15f),
                                            Cyan40.copy(alpha = 0.03f),
                                            Color.Transparent
                                        )
                                    ),
                                    radius = size.minDimension / 2
                                )
                            }
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "CNN",
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Black,
                            color = Cyan40,
                            letterSpacing = 8.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "CONVERSORES",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary,
                            letterSpacing = 6.sp,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(Modifier.height(44.dp))

                // Glass card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    GlassHighlight,
                                    Dark10.copy(alpha = 0.95f),
                                    Dark10
                                )
                            )
                        )
                        .padding(28.dp)
                ) {
                    Text(
                        "Entrar",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimaryColor
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Acesse sua conta para continuar",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    AnimatedVisibility(
                        visible = showExpiredNotice,
                        enter = fadeIn() + slideInVertically()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFFFF8F00).copy(alpha = 0.12f),
                                            Color(0xFFFF8F00).copy(alpha = 0.05f)
                                        )
                                    )
                                )
                                .border(1.dp, Color(0xFFFF8F00).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Sessão encerrada por inatividade",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFB74D)
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it; error = null; showExpiredNotice = false },
                        label = { Text("Usuário") },
                        leadingIcon = { Icon(Icons.Default.Person, null, Modifier.size(20.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text("Senha") },
                        leadingIcon = { Icon(Icons.Default.Lock, null, Modifier.size(20.dp)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { doLogin() })
                    )

                    AnimatedVisibility(
                        visible = error != null,
                        enter = fadeIn() + slideInVertically()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                error ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    Button(
                        onClick = { doLogin() },
                        enabled = !loading && username.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Cyan40,
                            disabledContainerColor = Dark20
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 2.dp,
                            disabledElevation = 0.dp
                        )
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = Color(0xFF00131E)
                            )
                        } else {
                            Text(
                                "ENTRAR",
                                style = MaterialTheme.typography.labelLarge,
                                letterSpacing = 3.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (biometricAvailable) {
                        Spacer(Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(Modifier.weight(1f), color = Dark20)
                            Text(
                                "  ou  ",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            HorizontalDivider(Modifier.weight(1f), color = Dark20)
                        }

                        Spacer(Modifier.height(20.dp))

                        // Pulsing glow on biometric button
                        val bioPulse by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 0.7f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "bioPulse"
                        )

                        OutlinedButton(
                            onClick = { doBiometricLogin() },
                            enabled = !loading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                1.dp,
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Cyan40.copy(alpha = bioPulse * 0.6f),
                                        Cyan60.copy(alpha = bioPulse),
                                        Cyan40.copy(alpha = bioPulse * 0.6f)
                                    )
                                )
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Cyan40,
                                containerColor = Cyan40.copy(alpha = 0.05f)
                            )
                        ) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = "Digital",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "ENTRAR COM DIGITAL",
                                style = MaterialTheme.typography.labelLarge,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                Text(
                    "v1.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary.copy(alpha = 0.4f)
                )
            }
        }
    }
}

private val TextPrimaryColor = Color(0xFFE8ECF1)
