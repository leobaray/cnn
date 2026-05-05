package com.lbwma.cnn.screen

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.lbwma.cnn.model.FILTROS
import com.lbwma.cnn.model.FOTOS_POR_FILTRO
import com.lbwma.cnn.model.PhotoCountCache
import com.lbwma.cnn.model.RefinementStore
import com.lbwma.cnn.network.ApiClient
import com.lbwma.cnn.network.UploadManager
import com.lbwma.cnn.network.UploadState
import com.lbwma.cnn.ui.SkeletonBox
import com.lbwma.cnn.ui.theme.Cyan40
import com.lbwma.cnn.ui.theme.Dark00
import com.lbwma.cnn.ui.theme.Dark10
import com.lbwma.cnn.ui.theme.Dark15
import com.lbwma.cnn.ui.theme.GlassBorder
import com.lbwma.cnn.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilterGridScreen(
    conversorName: String,
    onFilterClick: (filtroId: Int) -> Unit,
    onFilterLongClick: (filtroId: Int) -> Unit,
    onReviewClick: (filtroId: Int) -> Unit,
    onBack: () -> Unit
) {
    var filterCounts by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    val pendingMap by RefinementStore.pending.collectAsState()
    val uploadState by UploadManager.stateFor(conversorName).collectAsState(UploadState())
    var hadUploads by remember { mutableStateOf(uploadState.pending > 0) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val green = Color(0xFF4CAF50)
    val context = LocalContext.current
    val activity = context as Activity
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var pendingFiltroId by remember { mutableStateOf<Int?>(null) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val id = pendingFiltroId ?: return@rememberLauncherForActivityResult
        if (granted) onFilterClick(id)
        else {
            permanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            showPermissionDialog = true
        }
    }

    fun loadCounts() {
        loading = filterCounts.isEmpty()
        scope.launch {
            ApiClient.getFotos(conversorName)
                .onSuccess { fotos ->
                    val counts = FILTROS.associate { f ->
                        f.id to fotos.count { it.nome.startsWith("${f.prefix}_") }
                    }
                    filterCounts = counts
                    loading = false
                    // Atualiza cache: total + por filtro
                    PhotoCountCache.setCount(conversorName, fotos.size)
                    counts.forEach { (id, c) -> PhotoCountCache.setFilterCount(conversorName, id, c) }
                }
                .onFailure {
                    loading = false
                    snackbar.showSnackbar("Erro: ${it.message}")
                }
        }
    }

    LaunchedEffect(Unit) { loadCounts() }

    // Quando todos os uploads terminam, busca contagens reais do servidor e limpa completed
    LaunchedEffect(uploadState.pending) {
        when {
            uploadState.pending > 0 -> hadUploads = true
            hadUploads -> {
                hadUploads = false
                loadCounts()
                UploadManager.clearCompleted(conversorName)
            }
        }
    }

    // Contagem de uploads concluídos por filtro (incrementa em tempo real)
    val completedForConversor = uploadState.completed
    val uploadedPerFilter = remember(completedForConversor.size) {
        FILTROS.associate { f ->
            f.id to completedForConversor.count { it.startsWith("${f.prefix}_") }
        }
    }

    Scaffold(
        containerColor = Dark00,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Dark00, Dark00, Color.Transparent))
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Dark15)
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
                                conversorName,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    brush = com.lbwma.cnn.ui.heroGradient()
                                ),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "16 filtros · IA",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    IconButton(
                        onClick = { loadCounts() },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Dark15)
                    ) {
                        Icon(
                            Icons.Default.Refresh, "Atualizar",
                            tint = TextSecondary, modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (loading && filterCounts.isEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(16) {
                        SkeletonBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp),
                            cornerDp = 16
                        )
                    }
                }
            } else {
                // Total progress (servidor + uploads concluídos)
                val totalFotos = filterCounts.values.sum() + completedForConversor.size
                val totalTarget = FILTROS.size * FOTOS_POR_FILTRO

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header item spanning 2 columns
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        Column(
                            modifier = Modifier.padding(bottom = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "$totalFotos / $totalTarget fotos",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (totalFotos.toFloat() / totalTarget).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = if (totalFotos >= totalTarget) green else Cyan40,
                                trackColor = Dark15,
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }

                    itemsIndexed(FILTROS, key = { _, f -> f.id }) { index, filtro ->
                        val serverCount = filterCounts[filtro.id] ?: 0
                        val count = serverCount + (uploadedPerFilter[filtro.id] ?: 0)
                        val complete = count >= FOTOS_POR_FILTRO
                        val hasPending = pendingMap.containsKey("${conversorName}_${filtro.id}")

                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(300, delayMillis = index * 40)) +
                                    slideInVertically(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        initialOffsetY = { it / 3 }
                                    )
                        ) {
                            val accent = when {
                                complete -> green
                                hasPending -> Color(0xFFFBBF24)
                                else -> Cyan40
                            }
                            val accentLight = when {
                                complete -> Color(0xFF34D399)
                                hasPending -> Color(0xFFFCD34D)
                                else -> com.lbwma.cnn.ui.theme.Cyan60
                            }
                            val cardBg = Brush.verticalGradient(
                                listOf(
                                    accent.copy(alpha = if (complete) 0.10f else 0.04f),
                                    com.lbwma.cnn.ui.theme.Dark10,
                                    com.lbwma.cnn.ui.theme.Dark05
                                )
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .border(
                                        1.dp,
                                        Brush.linearGradient(
                                            listOf(
                                                accent.copy(alpha = if (complete) 0.45f else 0.18f),
                                                GlassBorder,
                                                GlassBorder
                                            )
                                        ),
                                        RoundedCornerShape(18.dp)
                                    )
                                    .background(cardBg)
                                    .combinedClickable(
                                        onClick = {
                                            when {
                                                hasPending -> onReviewClick(filtro.id)
                                                complete -> scope.launch { snackbar.showSnackbar("Filtro completo") }
                                                else -> {
                                                    pendingFiltroId = filtro.id
                                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                                }
                                            }
                                        },
                                        onLongClick = { onFilterLongClick(filtro.id) }
                                    )
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    // Prefix badge premium
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(accent.copy(alpha = 0.15f))
                                            .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            filtro.prefix.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accent,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    when {
                                        hasPending -> Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(accent.copy(alpha = 0.18f))
                                        ) {
                                            Icon(
                                                Icons.Default.HourglassTop,
                                                contentDescription = "Refinamento pendente",
                                                tint = accent,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        complete -> Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(accent.copy(alpha = 0.18f))
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Completo",
                                                tint = accent,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                Text(
                                    filtro.linha1,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = com.lbwma.cnn.ui.theme.TextPrimary
                                )
                                Text(
                                    filtro.linha2,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )

                                Spacer(Modifier.height(12.dp))

                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(com.lbwma.cnn.ui.theme.Dark20)
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(
                                                (count.toFloat() / FOTOS_POR_FILTRO).coerceIn(0f, 1f)
                                            )
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(
                                                Brush.horizontalGradient(listOf(accent, accentLight))
                                            )
                                    )
                                }

                                Spacer(Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "$count",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = accent
                                    )
                                    Text(
                                        "/ $FOTOS_POR_FILTRO",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            containerColor = Dark15,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    "Câmera necessária",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (permanentlyDenied)
                        "Para usar o aplicativo é necessário o acesso à câmera. Abra as configurações e conceda a permissão."
                    else
                        "Para usar o aplicativo é necessário o acesso à câmera. Conceda a permissão para continuar.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    if (permanentlyDenied) {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    } else {
                        pendingFiltroId?.let { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                    }
                }) {
                    Text(
                        if (permanentlyDenied) "Abrir configurações" else "Tentar novamente",
                        color = Cyan40,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}
