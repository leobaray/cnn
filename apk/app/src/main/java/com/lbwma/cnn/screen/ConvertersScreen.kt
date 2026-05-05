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
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import com.lbwma.cnn.model.PhotoCountCache
import com.lbwma.cnn.network.ApiClient
import com.lbwma.cnn.ui.SkeletonBox
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConvertersScreen(
    onConversorClick: (String) -> Unit,
    iaMode: Boolean,
    onIaModeChange: (Boolean) -> Unit
) {
    var conversores by remember { mutableStateOf<List<String>>(emptyList()) }
    val cachedCounts by PhotoCountCache.counts.collectAsState()
    val completedSet by PhotoCountCache.completedConversores.collectAsState()
    val fotoCounts by remember(cachedCounts) {
        derivedStateOf {
            conversores.associateWith { (cachedCounts[it] ?: 0) }
        }
    }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var selectedConversor by remember { mutableStateOf<String?>(null) }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    val filteredConversores by remember(conversores, search) {
        derivedStateOf {
            if (search.isBlank()) conversores
            else conversores.filter { it.contains(search.trim(), ignoreCase = true) }
        }
    }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    fun loadConversores(isRefresh: Boolean = false) {
        if (isRefresh) refreshing = true else loading = conversores.isEmpty()
        scope.launch {
            ApiClient.getConversores()
                .onSuccess { conversores = it; loading = false; refreshing = false }
                .onFailure { loading = false; refreshing = false; snackbar.showSnackbar("Erro: ${it.message}") }
        }
    }

    LaunchedEffect(Unit) { loadConversores() }

    // Carrega contagem de fotos de cada conversor em paralelo (atualiza cache em tempo real)
    LaunchedEffect(conversores) {
        if (conversores.isEmpty()) return@LaunchedEffect
        val jobs = conversores.map { nome ->
            async { nome to (ApiClient.getFotos(nome).getOrNull()?.size ?: 0) }
        }
        jobs.forEach { deferred ->
            val (nome, count) = deferred.await()
            PhotoCountCache.setCount(nome, count)
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
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Conversores",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    brush = com.lbwma.cnn.ui.heroGradient()
                                ),
                                fontWeight = FontWeight.Bold
                            )
                            if (conversores.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                val completos = completedSet.intersect(conversores.toSet()).size
                                Text(
                                    "${conversores.size} cadastrado${if (conversores.size != 1) "s" else ""}" +
                                        if (completos > 0) " · $completos completo${if (completos != 1) "s" else ""}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showSearch = !showSearch; if (!showSearch) search = "" },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (showSearch) Cyan40.copy(alpha = 0.2f) else Dark15)
                            ) {
                                Icon(
                                    androidx.compose.material.icons.Icons.Default.Search,
                                    "Buscar",
                                    tint = if (showSearch) Cyan40 else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(6.dp))
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

                    AnimatedVisibility(
                        visible = showSearch,
                        enter = fadeIn() + androidx.compose.animation.expandVertically(),
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
                    ) {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = { Text("Buscar conversor…", color = TextSecondary) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Cyan40,
                                unfocusedBorderColor = Dark20,
                                cursorColor = Cyan40,
                                focusedContainerColor = Dark15.copy(alpha = 0.5f),
                                unfocusedContainerColor = Dark15.copy(alpha = 0.3f),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Toggle de modo: Fotos / IA — full-width pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
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
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(if (selected) Cyan40 else Color.Transparent)
                                    .clickable { onIaModeChange(isIa) }
                                    .padding(vertical = 8.dp)
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
                }
            }
        },
        floatingActionButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(com.lbwma.cnn.ui.primaryGradient())
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    .clickable { showDialog = true }
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .drawBehind {
                        drawCircle(
                            color = Cyan40.copy(alpha = 0.30f),
                            radius = size.minDimension / 1.4f
                        )
                    }
            ) {
                Icon(Icons.Default.Add, "Novo", tint = com.lbwma.cnn.ui.theme.OnPrimaryDark, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Novo",
                    color = com.lbwma.cnn.ui.theme.OnPrimaryDark,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(6) {
                        SkeletonBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp),
                            cornerDp = 16
                        )
                    }
                }
                conversores.isEmpty() -> {
                    Column(
                        Modifier.align(Alignment.Center).padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(120.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(120.dp)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(Cyan40.copy(alpha = 0.10f), Color.Transparent)
                                        ),
                                        CircleShape
                                    )
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(com.lbwma.cnn.ui.theme.Dark15, com.lbwma.cnn.ui.theme.Dark10)
                                        )
                                    )
                                    .border(1.dp, GlassBorder, CircleShape)
                            ) {
                                Icon(
                                    Icons.Outlined.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = Cyan40.copy(alpha = 0.5f)
                                )
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                        Text(
                            "Nenhum conversor ainda",
                            style = MaterialTheme.typography.titleLarge,
                            color = com.lbwma.cnn.ui.theme.TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Toque em \"Novo\" para começar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
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
                        if (filteredConversores.isEmpty() && search.isNotBlank()) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(top = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Nenhum conversor encontrado",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        itemsIndexed(filteredConversores, key = { _, nome -> nome }) { index, nome ->
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
                                val complete = nome in completedSet
                                val count = fotoCounts[nome] ?: 0
                                val accent = if (complete) com.lbwma.cnn.ui.theme.Success else Cyan40
                                val accentLight = if (complete) com.lbwma.cnn.ui.theme.SuccessLight else Cyan60
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .border(
                                            1.dp,
                                            Brush.linearGradient(
                                                listOf(
                                                    accent.copy(alpha = if (complete) 0.4f else 0.15f),
                                                    GlassBorder,
                                                    GlassBorder,
                                                )
                                            ),
                                            RoundedCornerShape(20.dp)
                                        )
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    accent.copy(alpha = if (complete) 0.10f else 0.04f),
                                                    com.lbwma.cnn.ui.theme.Dark10,
                                                    com.lbwma.cnn.ui.theme.Dark05
                                                )
                                            )
                                        )
                                        .combinedClickable(
                                            onClick = { onConversorClick(nome) },
                                            onLongClick = {
                                                selectedConversor = nome
                                                showOptionsDialog = true
                                            }
                                        )
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Avatar com inicial / status
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(accent.copy(alpha = 0.25f), accent.copy(alpha = 0.10f))
                                                )
                                            )
                                            .border(1.dp, accent.copy(alpha = 0.4f), CircleShape)
                                    ) {
                                        Text(
                                            nome.firstOrNull()?.uppercase() ?: "?",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = accent
                                        )
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            nome,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = com.lbwma.cnn.ui.theme.TextPrimary
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (complete) {
                                                Icon(
                                                    androidx.compose.material.icons.Icons.Default.CheckCircle,
                                                    null,
                                                    tint = accent,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(Modifier.width(5.dp))
                                                Text(
                                                    "Completo · $count fotos",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = accent,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            } else if (count > 0) {
                                                Text(
                                                    "$count fotos",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextSecondary
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Box(
                                                    Modifier
                                                        .weight(1f)
                                                        .height(3.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(com.lbwma.cnn.ui.theme.Dark20)
                                                ) {
                                                    Box(
                                                        Modifier
                                                            .fillMaxWidth(
                                                                (count.toFloat() / 4400f).coerceIn(0f, 1f)
                                                            )
                                                            .height(3.dp)
                                                            .background(
                                                                Brush.horizontalGradient(
                                                                    listOf(accent, accentLight)
                                                                )
                                                            )
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    "Vazio",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextSecondary.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(com.lbwma.cnn.ui.theme.Dark15),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = accent,
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
            containerColor = Dark15,
            shape = RoundedCornerShape(28.dp),
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

    // Diálogo de opções (long press)
    if (showOptionsDialog && selectedConversor != null) {
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            containerColor = Dark15,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    selectedConversor!!,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            showOptionsDialog = false
                            renameText = selectedConversor!!
                            showRenameDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Renomear", color = Cyan40, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(
                        onClick = {
                            showOptionsDialog = false
                            showDeleteDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Apagar", color = Color(0xFFEF5350), fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showOptionsDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    // Diálogo de renomear
    if (showRenameDialog && selectedConversor != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = Dark15,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    "Renomear Conversor",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Novo nome para \"${selectedConversor}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
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
                        val novo = renameText.trim()
                        val atual = selectedConversor!!
                        if (novo.isNotEmpty() && novo != atual) {
                            showRenameDialog = false
                            scope.launch {
                                ApiClient.renameConversor(atual, novo)
                                    .onSuccess {
                                        PhotoCountCache.renameConversor(atual, novo)
                                        loadConversores()
                                        snackbar.showSnackbar("Renomeado para \"$novo\"")
                                    }
                                    .onFailure { snackbar.showSnackbar("Erro: ${it.message}") }
                            }
                        }
                    },
                    enabled = renameText.isNotBlank() && renameText.trim() != selectedConversor
                ) {
                    val enabled = renameText.isNotBlank() && renameText.trim() != selectedConversor
                    Text(
                        "Renomear",
                        color = if (enabled) Cyan40 else TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    // Diálogo de confirmar exclusão
    if (showDeleteDialog && selectedConversor != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = Dark15,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(
                    "Apagar Conversor",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Tem certeza que deseja apagar \"${selectedConversor}\"?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Todas as fotos serão excluídas permanentemente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFEF5350)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val nome = selectedConversor!!
                        showDeleteDialog = false
                        scope.launch {
                            ApiClient.deleteConversor(nome)
                                .onSuccess {
                                    PhotoCountCache.removeConversor(nome)
                                    loadConversores()
                                    snackbar.showSnackbar("\"$nome\" apagado")
                                }
                                .onFailure { snackbar.showSnackbar("Erro: ${it.message}") }
                        }
                    }
                ) {
                    Text("Apagar", color = Color(0xFFEF5350), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}
