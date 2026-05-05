package com.lbwma.cnn.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lbwma.cnn.model.FILTROS
import com.lbwma.cnn.model.FOTOS_POR_FILTRO
import com.lbwma.cnn.model.RefinementStore
import com.lbwma.cnn.network.ApiClient
import com.lbwma.cnn.network.UploadManager
import com.lbwma.cnn.ui.theme.Cyan40
import com.lbwma.cnn.ui.theme.Dark00
import com.lbwma.cnn.ui.theme.Dark10
import com.lbwma.cnn.ui.theme.Dark15
import com.lbwma.cnn.ui.theme.GlassBorder
import com.lbwma.cnn.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

private val Green = Color(0xFF4CAF50)
private val Red = Color(0xFFEF5350)

@Composable
fun ReviewScreen(
    conversorName: String,
    filtroId: Int,
    serverCount: Int,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val filtro = remember { FILTROS.first { it.id == filtroId } }
    val gridFiles = remember { mutableStateListOf<File>().apply { addAll(RefinementStore.load(conversorName, filtroId)) } }
    val swipeFiles = remember { mutableStateListOf<File>() }
    val keptFiles = remember { mutableStateListOf<File>() }
    var phase by remember { mutableStateOf("grid") } // "grid", "swipe", "summary"
    var currentIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    var fullscreenFile by remember { mutableStateOf<File?>(null) }

    // Ao cancelar, sincroniza o store com os arquivos que ainda existem
    fun cancelAndSync() {
        val remaining = when (phase) {
            "grid" -> gridFiles.toList()
            "swipe" -> {
                // Arquivos ainda não revisados + mantidos
                val notReviewed = if (currentIndex < swipeFiles.size) swipeFiles.subList(currentIndex, swipeFiles.size) else emptyList()
                notReviewed + keptFiles
            }
            else -> emptyList()
        }
        if (remaining.isNotEmpty()) {
            RefinementStore.save(conversorName, filtroId, remaining)
        } else {
            RefinementStore.clear(conversorName, filtroId)
        }
        onCancel()
    }

    var fetchedServerCount by remember { mutableIntStateOf(serverCount) }
    LaunchedEffect(Unit) {
        ApiClient.getFotos(conversorName)
            .onSuccess { fotos ->
                fetchedServerCount = fotos.count { it.nome.startsWith("${filtro.prefix}_") }
            }
    }

    val maxKeep = (FOTOS_POR_FILTRO - fetchedServerCount).coerceAtLeast(0)

    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val threshold = screenWidthPx * 0.4f

    // Grid vazio: todas apagadas na fase 1 → encerrar revisão
    LaunchedEffect(gridFiles.size) {
        if (phase == "grid" && gridFiles.isEmpty()) {
            RefinementStore.clear(conversorName, filtroId)
            onDone()
        }
    }

    // Auto-finish: quando atingir o máximo, ir direto pro resumo
    LaunchedEffect(keptFiles.size) {
        if (phase == "swipe" && maxKeep > 0 && keptFiles.size >= maxKeep) {
            // Apagar as restantes que não foram revisadas
            for (i in currentIndex until swipeFiles.size) {
                swipeFiles[i].delete()
            }
            phase = "summary"
        }
    }

    when (phase) {
        // ===================== FASE 1 — GRID =====================
        "grid" -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Dark00)
            ) {
                Column(Modifier.fillMaxSize()) {
                    // Top bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { cancelAndSync() },
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
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Limpeza rápida",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${gridFiles.size} fotos · toque no X para remover",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    // Grid 2 colunas
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(gridFiles, key = { it.absolutePath }) { file ->
                            Box(
                                modifier = Modifier
                                    .animateItem()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                    .clickable { fullscreenFile = file }
                            ) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Botão X
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .border(1.dp, Red.copy(alpha = 0.5f), CircleShape)
                                        .clickable {
                                            file.delete()
                                            gridFiles.remove(file)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Close, null,
                                        tint = Red,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Botão continuar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Dark00, Dark00)
                                )
                            )
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        val canReview = gridFiles.isNotEmpty()
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (canReview) com.lbwma.cnn.ui.primaryGradient()
                                    else Brush.horizontalGradient(listOf(Dark10, Dark10))
                                )
                                .border(
                                    1.dp,
                                    if (canReview) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                                    RoundedCornerShape(16.dp)
                                )
                                .let {
                                    if (canReview) it.clickable {
                                        swipeFiles.addAll(gridFiles)
                                        currentIndex = 0
                                        phase = "swipe"
                                    } else it
                                }
                        ) {
                            Text(
                                "Revisar ${gridFiles.size} fotos",
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (canReview) com.lbwma.cnn.ui.theme.OnPrimaryDark else TextSecondary
                            )
                        }
                    }
                }

                // Fullscreen overlay
                if (fullscreenFile != null) {
                    FullscreenLocalPhoto(
                        file = fullscreenFile!!,
                        onClose = { fullscreenFile = null }
                    )
                }
            }
        }

        // ===================== FASE 2 — SWIPE =====================
        "swipe" -> {
            val reviewing = currentIndex < swipeFiles.size
            if (!reviewing) {
                phase = "summary"
                return
            }

            val currentFile = swipeFiles[currentIndex]
            val dragProgress = (offsetX.value / threshold).coerceIn(-1f, 1f)

            fun keepCurrent() {
                scope.launch {
                    offsetX.animateTo(screenWidthPx, tween(200))
                    keptFiles.add(currentFile)
                    currentIndex++
                    offsetX.snapTo(0f)
                }
            }

            fun deleteCurrent() {
                scope.launch {
                    offsetX.animateTo(-screenWidthPx, tween(200))
                    currentFile.delete()
                    currentIndex++
                    offsetX.snapTo(0f)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Dark00)
            ) {
                // Photo com swipe
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 100.dp, bottom = 140.dp, start = 16.dp, end = 16.dp)
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
                                            keepCurrent()
                                        } else if (totalDrag < -threshold) {
                                            deleteCurrent()
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

                    // Green overlay — keep
                    if (dragProgress > 0.1f) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Green.copy(alpha = dragProgress * 0.4f))
                        )
                    }
                    // Red overlay — delete
                    if (dragProgress < -0.1f) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Red.copy(alpha = -dragProgress * 0.4f))
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
                        onClick = { cancelAndSync() },
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
                            "${currentIndex + 1} / ${swipeFiles.size}",
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
                        val countColor = if (keptFiles.size >= maxKeep) Green else Cyan40
                        Text(
                            "Mantidas: ${keptFiles.size}/$maxKeep",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = countColor
                        )
                    }
                }

                // Bottom — botões clicáveis
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp, start = 32.dp, end = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botão Apagar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(28.dp))
                            .background(Red.copy(alpha = 0.12f))
                            .border(1.dp, Red.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
                            .clickable { deleteCurrent() }
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Red, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Apagar", color = Red, fontWeight = FontWeight.SemiBold)
                    }

                    // Indicador central
                    Text(
                        "ou arraste",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary.copy(alpha = 0.5f)
                    )

                    // Botão Manter
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(28.dp))
                            .background(Green.copy(alpha = 0.12f))
                            .border(1.dp, Green.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
                            .clickable { keepCurrent() }
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text("Manter", color = Green, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Check, null, tint = Green, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // ===================== RESUMO =====================
        "summary" -> {
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
                        "$uploadCount fotos serão enviadas",
                        style = MaterialTheme.typography.titleMedium,
                        color = Green
                    )
                    if (excess > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "$excess fotos extras descartadas (limite $FOTOS_POR_FILTRO)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Red
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val totalOriginal = gridFiles.size
                    val totalRemoved = totalOriginal - keptFiles.size
                    Text(
                        "$totalRemoved fotos removidas no total",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(32.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { cancelAndSync() }) {
                            Text("Cancelar", color = TextSecondary)
                        }
                        TextButton(
                            onClick = {
                                val toUpload = keptFiles.take(maxKeep)
                                keptFiles.drop(maxKeep).forEach { it.delete() }
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
        }
    }
}

@Composable
private fun FullscreenLocalPhoto(file: File, onClose: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offset = Offset(offset.x + pan.x, offset.y + pan.y)
                        } else {
                            offset = Offset.Zero
                        }
                    }
                }
        )

        // Botão fechar
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(14.dp)
                .size(42.dp)
                .clip(CircleShape)
                .background(Dark15.copy(alpha = 0.8f))
                .border(1.dp, GlassBorder, CircleShape)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "Fechar",
                tint = Color.White, modifier = Modifier.size(20.dp)
            )
        }
    }
}
