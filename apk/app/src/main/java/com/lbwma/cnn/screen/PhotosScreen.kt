package com.lbwma.cnn.screen

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.lbwma.cnn.network.ApiClient
import com.lbwma.cnn.network.Foto
import com.lbwma.cnn.network.ThumbnailCache
import com.lbwma.cnn.network.UploadManager
import com.lbwma.cnn.network.UploadState
import com.lbwma.cnn.ui.theme.Cyan40
import com.lbwma.cnn.ui.theme.Dark00
import com.lbwma.cnn.ui.theme.Dark10
import com.lbwma.cnn.ui.theme.Dark15
import com.lbwma.cnn.ui.theme.Dark20
import com.lbwma.cnn.ui.theme.GlassBorder
import com.lbwma.cnn.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    conversorName: String,
    imageLoader: ImageLoader,
    filesToUpload: List<File>,
    filterPrefix: String? = null,
    onFilesConsumed: () -> Unit,
    onOpenCamera: () -> Unit,
    onViewPhoto: (String) -> Unit,
    onBack: () -> Unit
) {
    var fotos by remember { mutableStateOf<List<Foto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val uploadState by UploadManager.stateFor(conversorName).collectAsState(UploadState())
    var hadUploads by remember { mutableStateOf(uploadState.pending > 0) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    val activity = context as Activity

    fun loadFotos(isRefresh: Boolean = false) {
        if (isRefresh) refreshing = true else loading = true
        scope.launch {
            ApiClient.getFotos(conversorName)
                .onSuccess { result ->
                    fotos = if (filterPrefix != null) result.filter { it.nome.startsWith(filterPrefix) } else result
                    loading = false; refreshing = false
                }
                .onFailure { loading = false; refreshing = false; snackbar.showSnackbar("Erro: ${it.message}") }
        }
    }

    LaunchedEffect(uploadState.pending) {
        when {
            uploadState.pending > 0 -> hadUploads = true
            hadUploads -> {
                hadUploads = false
                loadFotos()
                snackbar.showSnackbar("Upload concluído")
            }
        }
    }

    LaunchedEffect(uploadState.lastError) {
        val err = uploadState.lastError ?: return@LaunchedEffect
        snackbar.showSnackbar(err)
        UploadManager.clearError(conversorName)
    }

    LaunchedEffect(filesToUpload) {
        if (filesToUpload.isNotEmpty()) {
            snackbar.showSnackbar("Enviando ${filesToUpload.size} foto(s)...")
            UploadManager.enqueue(conversorName, filesToUpload)
            onFilesConsumed()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val bytes = context.contentResolver.openInputStream(it)?.readBytes() ?: return@launch
                    val tempFile = File(context.cacheDir, "gallery_${System.currentTimeMillis()}.jpg")
                    tempFile.writeBytes(bytes)
                    UploadManager.enqueue(conversorName, listOf(tempFile))
                } catch (_: Exception) {}
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onOpenCamera()
        else {
            permanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            showPermissionDialog = true
        }
    }

    LaunchedEffect(Unit) { loadFotos() }

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
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Dark15)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            conversorName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val subtitle = when {
                            filterPrefix != null && fotos.isNotEmpty() -> "${fotos.size} foto${if (fotos.size != 1) "s" else ""} · $filterPrefix"
                            uploadState.pending > 0 -> "Enviando ${uploadState.pending} foto(s)..."
                            fotos.isNotEmpty() -> "${fotos.size} foto${if (fotos.size != 1) "s" else ""}"
                            else -> null
                        }
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                    IconButton(
                        onClick = { loadFotos(isRefresh = true) },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Dark15)
                    ) {
                        Icon(Icons.Default.Refresh, "Atualizar", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        floatingActionButton = {
            if (filterPrefix != null) return@Scaffold
            Box {
                FloatingActionButton(
                    onClick = { showMenu = true },
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
                    Icon(Icons.Default.Add, "Adicionar foto", modifier = Modifier.size(26.dp))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = Dark10,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Tirar fotos") },
                        onClick = { showMenu = false; cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        leadingIcon = { Icon(Icons.Default.CameraAlt, null, tint = Cyan40, modifier = Modifier.size(20.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Escolher da galeria") },
                        onClick = { showMenu = false; galleryLauncher.launch("image/*") },
                        leadingIcon = { Icon(Icons.Default.Image, null, tint = Cyan40, modifier = Modifier.size(20.dp)) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (uploadState.pending > 0) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .align(Alignment.TopCenter),
                    color = Cyan40,
                    trackColor = Dark15,
                    strokeCap = StrokeCap.Round
                )
            }
            when {
                loading -> CircularProgressIndicator(
                    Modifier.align(Alignment.Center), color = Cyan40, strokeWidth = 2.5.dp
                )
                fotos.isEmpty() -> Column(
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
                            Icons.Outlined.Collections,
                            contentDescription = null,
                            modifier = Modifier.size(42.dp),
                            tint = TextSecondary.copy(alpha = 0.4f)
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Nenhuma foto",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Toque em + para adicionar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
                else -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { loadFotos(isRefresh = true) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(108.dp),
                        contentPadding = PaddingValues(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(fotos, key = { it.nome }) { foto ->
                            val thumbFile = remember(foto.nome) { ThumbnailCache.getFile(conversorName, foto.nome) }
                            var thumbReady by rememberSaveable(foto.nome) { mutableStateOf(thumbFile.exists()) }

                            LaunchedEffect(foto.nome) {
                                if (!thumbReady && ThumbnailCache.generate(conversorName, foto.nome)) {
                                    thumbReady = true
                                }
                            }

                            Box(
                                Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                                    .background(Dark10)
                                    .clickable { onViewPhoto(foto.nome) }
                            ) {
                                if (thumbReady) {
                                    AsyncImage(
                                        model = thumbFile,
                                        contentDescription = foto.nome,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        Modifier.size(20.dp).align(Alignment.Center),
                                        strokeWidth = 2.dp,
                                        color = Cyan40
                                    )
                                }
                                // Top gradient for delete button
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .align(Alignment.TopEnd)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                                            )
                                        )
                                )
                                IconButton(
                                    onClick = { deleteTarget = foto.nome },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.3f)),
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Transparent)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        "Deletar",
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(14.dp)
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
            containerColor = Dark10,
            shape = RoundedCornerShape(24.dp),
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
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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

    deleteTarget?.let { arquivo ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = Dark10,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    "Deletar foto",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text("Tem certeza que deseja deletar $arquivo?", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    val name = arquivo; deleteTarget = null
                    scope.launch {
                        ApiClient.deleteFoto(conversorName, name)
                            .onSuccess { ThumbnailCache.getFile(conversorName, name).delete(); loadFotos(); snackbar.showSnackbar("\"$name\" deletada") }
                            .onFailure { snackbar.showSnackbar("Erro ao deletar") }
                    }
                }) { Text("Deletar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar", color = TextSecondary) }
            }
        )
    }
}
