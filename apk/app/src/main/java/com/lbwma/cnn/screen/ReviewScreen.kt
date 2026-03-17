package com.lbwma.cnn.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lbwma.cnn.model.FILTROS
import com.lbwma.cnn.model.FOTOS_POR_FILTRO
import com.lbwma.cnn.model.RefinementStore
import com.lbwma.cnn.network.UploadManager
import com.lbwma.cnn.ui.theme.Cyan40
import com.lbwma.cnn.ui.theme.Dark00
import com.lbwma.cnn.ui.theme.Dark10
import com.lbwma.cnn.ui.theme.Dark15
import com.lbwma.cnn.ui.theme.GlassBorder
import com.lbwma.cnn.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ReviewScreen(
    conversorName: String,
    filtroId: Int,
    serverCount: Int,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val filtro = remember { FILTROS.first { it.id == filtroId } }
    val allFiles = remember { RefinementStore.load(conversorName, filtroId) }
    val keptFiles = remember { mutableStateListOf<File>() }
    var currentIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val green = Color(0xFF4CAF50)
    val red = Color(0xFFEF5350)

    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val threshold = screenWidthPx * 0.3f
    val maxKeep = (FOTOS_POR_FILTRO - serverCount).coerceAtLeast(0)
    val reviewing = currentIndex < allFiles.size

    if (!reviewing) {
        // Review finished — summary
        val excess = (keptFiles.size - maxKeep).coerceAtLeast(0)
        val uploadCount = keptFiles.size.coerceAtMost(maxKeep)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Dark00)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    "${filtro.linha1}\n${filtro.linha2}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "$uploadCount fotos serao enviadas",
                    style = MaterialTheme.typography.titleMedium,
                    color = green
                )
                if (excess > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$excess fotos extras descartadas (limite $FOTOS_POR_FILTRO)",
                        style = MaterialTheme.typography.bodySmall,
                        color = red
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${allFiles.size - keptFiles.size} fotos removidas",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(32.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onCancel) {
                        Text("Cancelar", color = TextSecondary)
                    }
                    TextButton(
                        onClick = {
                            val toUpload = keptFiles.take(maxKeep)
                            // Discard excess files
                            keptFiles.drop(maxKeep).forEach { it.delete() }
                            // Upload kept
                            UploadManager.enqueue(conversorName, toUpload)
                            RefinementStore.clear(conversorName, filtroId)
                            onDone()
                        }
                    ) {
                        Text("Confirmar envio", color = Cyan40, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    // Reviewing — show current photo with swipe
    val currentFile = allFiles[currentIndex]
    val dragProgress = (offsetX.value / threshold).coerceIn(-1f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Dark00)
    ) {
        // Photo
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 100.dp, bottom = 120.dp, start = 16.dp, end = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .graphicsLayer {
                    translationX = offsetX.value
                    rotationZ = dragProgress * 8f
                }
                .pointerInput(currentIndex) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { _ -> totalDrag = 0f },
                        onDragEnd = {
                            scope.launch {
                                if (totalDrag > threshold) {
                                    offsetX.animateTo(screenWidthPx, tween(200))
                                    keptFiles.add(currentFile)
                                    currentIndex++
                                    offsetX.snapTo(0f)
                                } else if (totalDrag < -threshold) {
                                    offsetX.animateTo(-screenWidthPx, tween(200))
                                    currentFile.delete()
                                    currentIndex++
                                    offsetX.snapTo(0f)
                                } else {
                                    offsetX.animateTo(0f, tween(200))
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f, tween(200)) }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            totalDrag += dragAmount
                            scope.launch { offsetX.snapTo(totalDrag) }
                        }
                    )
                }
        ) {
            AsyncImage(
                model = currentFile,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            // Green overlay — dragging right (keep)
            if (dragProgress > 0.1f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(green.copy(alpha = dragProgress * 0.4f))
                )
            }
            // Red overlay — dragging left (delete)
            if (dragProgress < -0.1f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(red.copy(alpha = -dragProgress * 0.4f))
                )
            }
        }

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Dark15.copy(alpha = 0.7f))
                    .border(1.dp, GlassBorder, CircleShape)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "Voltar",
                    tint = Color.White, modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "${filtro.prefix} — ${filtro.linha1}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${currentIndex + 1} / ${allFiles.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.weight(1f))
            // Kept counter
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Dark15.copy(alpha = 0.7f))
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                val countColor = if (keptFiles.size > maxKeep) red else green
                Text(
                    "Mantidas: ${keptFiles.size}/$maxKeep",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = countColor
                )
            }
        }

        // Bottom hints
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp, start = 48.dp, end = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Close, null, tint = red.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text("Apagar", color = red.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Manter", color = green.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.Check, null, tint = green.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
            }
        }
    }
}
