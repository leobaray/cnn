package com.lbwma.cnn.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lbwma.cnn.BiometricHelper
import com.lbwma.cnn.model.IdentificationHistory
import com.lbwma.cnn.network.ApiClient
import com.lbwma.cnn.ui.theme.Cyan40
import com.lbwma.cnn.ui.theme.Dark00
import com.lbwma.cnn.ui.theme.Dark10
import com.lbwma.cnn.ui.theme.Dark15
import com.lbwma.cnn.ui.theme.GlassBorder
import com.lbwma.cnn.ui.theme.GlassHighlight
import com.lbwma.cnn.ui.theme.TextPrimary
import com.lbwma.cnn.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    val versionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
    }
    val versionCode = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        } catch (_: Exception) { 0L }
    }
    val biometricSaved = remember { BiometricHelper.hasSavedCredentials(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Dark00)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Dark15)
                        .border(1.dp, GlassBorder, CircleShape)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Voltar",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    "Configurações",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        brush = com.lbwma.cnn.ui.heroGradient()
                    ),
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // Conta
                SectionHeader("Conta")

                if (biometricSaved) {
                    SettingItem(
                        icon = Icons.Default.Fingerprint,
                        iconTint = Cyan40,
                        title = "Login biométrico",
                        subtitle = "Credenciais salvas para login rápido",
                        onClick = null
                    )
                }

                SettingItem(
                    icon = Icons.AutoMirrored.Filled.Logout,
                    iconTint = MaterialTheme.colorScheme.error,
                    title = "Sair da conta",
                    subtitle = "Apaga as credenciais e sessão",
                    onClick = { showLogoutDialog = true }
                )

                Spacer(Modifier.height(8.dp))
                SectionHeader("Dados")

                SettingItem(
                    icon = Icons.Default.History,
                    iconTint = Cyan40,
                    title = "Limpar histórico de identificações",
                    subtitle = "Apaga as últimas identificações",
                    onClick = { showClearHistoryDialog = true }
                )

                SettingItem(
                    icon = Icons.Default.CleaningServices,
                    iconTint = Cyan40,
                    title = "Limpar cache de imagens",
                    subtitle = "Libera espaço e recarrega miniaturas",
                    onClick = { showClearCacheDialog = true }
                )

                Spacer(Modifier.height(8.dp))
                SectionHeader("Sobre")

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Cyan40.copy(alpha = 0.10f),
                                    Dark15,
                                    Dark10
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.linearGradient(listOf(Cyan40.copy(alpha = 0.3f), GlassBorder)),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(com.lbwma.cnn.ui.heroGradient())
                        ) {
                            Icon(Icons.Default.Info, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "CNN Conversores",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                "v$versionName · build $versionCode",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = Dark15,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    "Sair da conta?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Você precisará fazer login novamente. Credenciais biométricas serão apagadas.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    BiometricHelper.clearCredentials(context)
                    ApiClient.logout()
                    onLoggedOut()
                }) {
                    Text("Sair", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            containerColor = Dark15,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Limpar histórico?", fontWeight = FontWeight.Bold) },
            text = { Text("Apagar todas as identificações recentes?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    IdentificationHistory.clear()
                    showClearHistoryDialog = false
                }) {
                    Text("Limpar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            containerColor = Dark15,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Limpar cache?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Miniaturas e cache de imagens serão removidos. Próxima abertura recarrega do servidor.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        java.io.File(context.filesDir, "thumbnails").deleteRecursively()
                        java.io.File(context.cacheDir, "image_cache").deleteRecursively()
                    } catch (_: Exception) {}
                    showClearCacheDialog = false
                }) {
                    Text("Limpar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary.copy(alpha = 0.6f),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp)
    )
}

@Composable
private fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(GlassHighlight, Dark10)))
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f))
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
