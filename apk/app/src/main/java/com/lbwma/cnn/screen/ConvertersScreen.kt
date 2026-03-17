package com.lbwma.cnn.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertersScreen(
    onConversorClick: (String) -> Unit,
    iaMode: Boolean,
    onIaModeChange: (Boolean) -> Unit
) {
    var conversores by remember { mutableStateOf<List<String>>(emptyList()) }
    var fotoCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    fun loadConversores(isRefresh: Boolean = false) {
        if (isRefresh) refreshing = true else loading = true
        scope.launch {
            ApiClient.getConversores()
                .onSuccess { conversores = it; loading = false; refreshing = false }
                .onFailure { loading = false; refreshing = false; snackbar.showSnackbar("Erro: ${it.message}") }
        }
    }

    LaunchedEffect(Unit) { loadConversores() }

    // Carrega contagem de fotos de cada conversor em paralelo
    LaunchedEffect(conversores) {
        if (conversores.isEmpty()) return@LaunchedEffect
        fotoCounts = emptyMap()
        val jobs = conversores.map { nome ->
            async { nome to (ApiClient.getFotos(nome).getOrNull()?.size ?: 0) }
        }
        jobs.forEach { deferred ->
            val (nome, count) = deferred.await()
            fotoCounts = fotoCounts + (nome to count)
        }
    }

    Scaffold(
        containerColor = Dark00,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Dark00, Dark00, Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Conversores",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (conversores.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${conversores.size} cadastrado${if (conversores.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Toggle de modo: Fotos / IA
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(Dark15)
                                .border(1.dp, GlassBorder, RoundedCornerShape(50.dp))
                                .padding(3.dp)
                        ) {
                            listOf("Fotos" to false, "IA" to true).forEach { (label, isIa) ->
                                val selected = iaMode == isIa
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50.dp))
                                        .background(if (selected) Cyan40 else Color.Transparent)
                                        .clickable { onIaModeChange(isIa) }
                                        .padding(horizontal = 18.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selected) Color(0xFF00131E) else TextSecondary
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { loadConversores(isRefresh = true) },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Dark15)
                        ) {
                            Icon(Icons.Default.Refresh, "Atualizar", tint = TextSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = Cyan40,
                contentColor = Color(0xFF00131E),
                shape = RoundedCornerShape(18.dp),
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 12.dp,
                    pressedElevation = 4.dp
                ),
                modifier = Modifier
                    .drawBehind {
                        drawCircle(
                            color = Cyan40.copy(alpha = 0.25f),
                            radius = size.minDimension / 1.5f
                        )
                    }
            ) {
                Icon(Icons.Default.Add, "Novo conversor", modifier = Modifier.size(26.dp))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center),
                    color = Cyan40,
                    strokeWidth = 2.5.dp
                )
                conversores.isEmpty() -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(Dark10)
                                .border(1.dp, GlassBorder, CircleShape)
                        ) {
                            Icon(
                                Icons.Outlined.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(42.dp),
                                tint = TextSecondary.copy(alpha = 0.4f)
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Nenhum conversor",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Toque em + para criar o primeiro",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { loadConversores(isRefresh = true) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(conversores, key = { _, nome -> nome }) { index, nome ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(300, delayMillis = index * 50)) +
                                        slideInVertically(
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            ),
                                            initialOffsetY = { it / 3 }
                                        ),
                                modifier = Modifier.animateItem()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    GlassHighlight,
                                                    Dark10,
                                                    Dark10
                                                )
                                            )
                                        )
                                        .clickable { onConversorClick(nome) }
                                        .padding(horizontal = 20.dp, vertical = 18.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Gradient accent bar — verde quando atingir 4400 fotos
                                    val complete = (fotoCounts[nome] ?: 0) >= 4400
                                    val barTop = if (complete) Color(0xFF4CAF50) else Cyan40
                                    val barBottom = if (complete) Color(0xFF4CAF50).copy(alpha = 0.3f) else Cyan60.copy(alpha = 0.3f)
                                    Box(
                                        Modifier
                                            .width(3.dp)
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(
                                                Brush.verticalGradient(colors = listOf(barTop, barBottom))
                                            )
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            nome,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Dark15),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false; newName = "" },
            containerColor = Dark10,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    "Novo Conversor",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Insira o nome do conversor",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nome") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cyan40,
                            unfocusedBorderColor = Dark20,
                            focusedLabelColor = Cyan40,
                            cursorColor = Cyan40,
                            focusedContainerColor = Dark15.copy(alpha = 0.5f),
                            unfocusedContainerColor = Dark15.copy(alpha = 0.3f),
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newName.trim()
                        if (name.isNotEmpty()) {
                            showDialog = false; newName = ""
                            scope.launch {
                                ApiClient.createConversor(name)
                                    .onSuccess { loadConversores(); snackbar.showSnackbar("\"$name\" criado") }
                                    .onFailure { snackbar.showSnackbar("Erro ao criar: ${it.message}") }
                            }
                        }
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text(
                        "Criar",
                        color = if (newName.isNotBlank()) Cyan40 else TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false; newName = "" }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}
