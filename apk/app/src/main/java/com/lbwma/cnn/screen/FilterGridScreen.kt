package com.lbwma.cnn.screen

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val uploadState by UploadManager.state.collectAsState()
    var hadUploads by remember { mutableStateOf(uploadState.pending > 0) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val green = Color(0xFF4CAF50)

    fun loadCounts() {
        loading = true
        scope.launch {
            ApiClient.getFotos(conversorName)
                .onSuccess { fotos ->
                    val counts = FILTROS.associate { f ->
                        f.id to fotos.count { it.nome.startsWith("${f.prefix}_") }
                    }
                    filterCounts = counts
                    loading = false
                }
                .onFailure {
                    loading = false
                    snackbar.showSnackbar("Erro: ${it.message}")
                }
        }
    }

    LaunchedEffect(Unit) { loadCounts() }

    LaunchedEffect(uploadState.pending) {
        when {
            uploadState.pending > 0 -> hadUploads = true
            hadUploads -> { hadUploads = false; loadCounts() }
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
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "16 filtros · IA",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
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
                CircularProgressIndicator(
                    Modifier.align(Alignment.Center),
                    color = Cyan40,
                    strokeWidth = 2.5.dp
                )
            } else {
                // Total progress
                val totalFotos = filterCounts.values.sum()
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
                        val count = filterCounts[filtro.id] ?: 0
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
                            val cardBg = if (complete) {
                                Brush.horizontalGradient(
                                    listOf(green.copy(alpha = 0.1f), Dark10)
                                )
                            } else {
                                Brush.horizontalGradient(listOf(Dark10, Dark10))
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                                    .background(cardBg)
                                    .combinedClickable(
                                        onClick = {
                                            when {
                                                hasPending -> onReviewClick(filtro.id)
                                                complete -> scope.launch { snackbar.showSnackbar("Filtro completo") }
                                                else -> onFilterClick(filtro.id)
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
                                    Column {
                                        Text(
                                            filtro.linha1,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            filtro.linha2,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                    when {
                                        hasPending -> Icon(
                                            Icons.Default.HourglassTop,
                                            contentDescription = "Refinamento pendente",
                                            tint = Color(0xFFFFB74D),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        complete -> Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Completo",
                                            tint = green,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                LinearProgressIndicator(
                                    progress = { (count.toFloat() / FOTOS_POR_FILTRO).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = if (complete) green else Cyan40,
                                    trackColor = Dark15,
                                    strokeCap = StrokeCap.Round
                                )

                                Spacer(Modifier.height(6.dp))

                                Text(
                                    "$count / $FOTOS_POR_FILTRO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (complete) green else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
