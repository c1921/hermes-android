package com.nousresearch.hermes.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.data.WorkspaceFilePreview
import com.nousresearch.hermes.data.WorkspacePreviewKind
import com.nousresearch.hermes.protocol.ManagedFileEntry
import com.nousresearch.hermes.platform.fileOpenIntent
import com.nousresearch.hermes.platform.fileShareIntent
import com.nousresearch.hermes.platform.sharedFileUri
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun WorkspaceFilesScreen(
    backend: BackendConfig,
    initialPath: String?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    viewModel: WorkspaceFilesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pathInput by rememberSaveable { mutableStateOf(initialPath.orEmpty()) }
    var pendingDownload by remember { mutableStateOf<ManagedFileEntry?>(null) }
    var externalActionBusy by remember { mutableStateOf(false) }
    var externalActionError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val entry = pendingDownload
        pendingDownload = null
        if (result.resultCode == android.app.Activity.RESULT_OK && entry != null) {
            result.data?.data?.let { viewModel.download(entry, it) }
        }
    }

    LaunchedEffect(backend.id, initialPath) { viewModel.bind(backend, initialPath) }
    LaunchedEffect(state.listing?.path) { state.listing?.path?.let { pathInput = it } }

    val backTarget = workspaceFilesBackTarget(
        previewOpen = state.preview != null,
        parentAvailable = state.listing?.parent != null,
        exitAvailable = onBack != null,
    )
    val navigateBack: (() -> Unit)? = when (backTarget) {
        WorkspaceFilesBackTarget.CLOSE_PREVIEW -> viewModel::closePreview
        WorkspaceFilesBackTarget.OPEN_PARENT -> ({ viewModel.open(state.listing?.parent) })
        WorkspaceFilesBackTarget.EXIT_FILES -> onBack
        null -> null
    }
    BackHandler(enabled = navigateBack != null) { navigateBack?.invoke() }

    fun openExternally(preview: WorkspaceFilePreview, share: Boolean) {
        scope.launch {
            externalActionBusy = true
            externalActionError = null
            runCatching {
                val uri = withContext(Dispatchers.IO) {
                    sharedFileUri(context, preview.entry.name, preview.contentBytes())
                }
                val intent = if (share) {
                    Intent.createChooser(fileShareIntent(uri, preview.mimeType, preview.entry.name), "Share ${preview.entry.name}")
                } else {
                    Intent.createChooser(fileOpenIntent(uri, preview.mimeType, preview.entry.name), "Open ${preview.entry.name} with")
                }
                context.startActivity(intent)
            }.onFailure { error ->
                externalActionError = error.message ?: "Android could not open another app for this file"
            }
            externalActionBusy = false
        }
    }

    Column(modifier.fillMaxSize()) {
        FilesHeader(
            path = state.preview?.entry?.name ?: state.listing?.path.orEmpty(),
            loading = state.loading || state.previewLoading,
            onRefresh = if (state.preview == null) viewModel::refresh else null,
            onBack = navigateBack,
        )
        state.error?.let { FileStatusSurface(it, error = true) }
        externalActionError?.let { FileStatusSurface(it, error = true) }
        state.notice?.let { FileStatusSurface(it, error = false) }
        state.downloading?.let { entry ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SAVING ${entry.name}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = viewModel::cancelDownload) { Icon(Icons.Outlined.Cancel, "Cancel download") }
                    }
                    state.downloadProgress?.let { LinearProgressIndicator(progress = { it }, Modifier.fillMaxWidth()) }
                        ?: LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        val preview = state.preview
        if (preview != null) {
            FilePreviewPane(
                preview = preview,
                busy = state.downloading != null || externalActionBusy,
                onSave = {
                    pendingDownload = preview.entry
                    createDocument.launch(createDocumentIntent(preview.entry))
                },
                onShare = { openExternally(preview, share = true) },
                onOpenWith = { openExternally(preview, share = false) },
                modifier = Modifier.weight(1f),
            )
        } else {
            val listing = state.listing
            if (listing?.canChangePath == true || (listing == null && state.error != null)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = pathInput,
                        onValueChange = { pathInput = it.take(4_096) },
                        singleLine = true,
                        label = { Text("Server path") },
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { viewModel.open(pathInput) }, enabled = pathInput.isNotBlank() && !state.loading) { Text("GO") }
                }
            }
            if (state.loading && listing == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    listing?.parent?.let { parent ->
                        item("parent:$parent") {
                            FileRow(
                                name = "..",
                                detail = "Parent directory",
                                directory = true,
                                onClick = { viewModel.open(parent) },
                            )
                        }
                    }
                    items(listing?.entries.orEmpty(), key = ManagedFileEntry::path) { entry ->
                        FileRow(
                            name = entry.name,
                            detail = if (entry.isDirectory) "Folder" else listOfNotNull(entry.mimeType, entry.size?.formatBytes()).joinToString(" / "),
                            directory = entry.isDirectory,
                            onClick = { if (entry.isDirectory) viewModel.open(entry.path) else viewModel.preview(entry) },
                            onSave = if (entry.isDirectory) null else ({
                                pendingDownload = entry
                                createDocument.launch(createDocumentIntent(entry))
                            }),
                        )
                    }
                    if (listing != null && listing.entries.isEmpty()) {
                        item("empty") {
                            Column(
                                Modifier.fillMaxWidth().padding(36.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("EMPTY WORKSPACE", style = MaterialTheme.typography.titleMedium)
                                Text("Hermes reported no files in this directory.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal enum class WorkspaceFilesBackTarget { CLOSE_PREVIEW, OPEN_PARENT, EXIT_FILES }

internal fun workspaceFilesBackTarget(
    previewOpen: Boolean,
    parentAvailable: Boolean,
    exitAvailable: Boolean,
): WorkspaceFilesBackTarget? = when {
    previewOpen -> WorkspaceFilesBackTarget.CLOSE_PREVIEW
    exitAvailable -> WorkspaceFilesBackTarget.EXIT_FILES
    parentAvailable -> WorkspaceFilesBackTarget.OPEN_PARENT
    else -> null
}

@Composable
private fun FilesHeader(path: String, loading: Boolean, onRefresh: (() -> Unit)?, onBack: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text("FILES", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(path.ifBlank { "Hermes workspace" }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        else onRefresh?.let { IconButton(onClick = it) { Icon(Icons.Outlined.Refresh, "Refresh files") } }
    }
}

@Composable
private fun FileStatusSurface(message: String, error: Boolean) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FileRow(
    name: String,
    detail: String,
    directory: Boolean,
    onClick: () -> Unit,
    onSave: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (directory) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (name == "..") Icon(Icons.Outlined.KeyboardArrowUp, null)
            onSave?.let { save -> IconButton(onClick = save) { Icon(Icons.Outlined.Download, "Save $name") } }
        }
    }
}

@Composable
private fun FilePreviewPane(
    preview: WorkspaceFilePreview,
    busy: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(preview.entry.name, style = MaterialTheme.typography.titleMedium)
                    Text("${preview.mimeType} / ${preview.entry.size?.formatBytes().orEmpty()}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onShare, enabled = !busy) {
                    Icon(Icons.Outlined.Share, "Share ${preview.entry.name}")
                }
                IconButton(onClick = onOpenWith, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open ${preview.entry.name} with another app")
                }
                OutlinedButton(onClick = onSave, enabled = !busy) {
                    Icon(Icons.Outlined.Download, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Save")
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxSize(),
        ) {
            when (preview.kind) {
                WorkspacePreviewKind.TEXT -> SelectionContainer {
                    Text(
                        preview.text,
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                WorkspacePreviewKind.HTML -> SandboxedHtmlPreview(preview.text)
                WorkspacePreviewKind.IMAGE -> RasterPreview(preview.bytes, preview.entry.name)
                WorkspacePreviewKind.PDF -> PdfPreview(preview.bytes)
            }
        }
    }
}

private fun WorkspaceFilePreview.contentBytes(): ByteArray = when (kind) {
    WorkspacePreviewKind.TEXT,
    WorkspacePreviewKind.HTML,
    -> text.toByteArray(Charsets.UTF_8)
    WorkspacePreviewKind.IMAGE,
    WorkspacePreviewKind.PDF,
    -> bytes
}

@Composable
private fun RasterPreview(bytes: ByteArray, name: String) {
    val result by produceState<Result<Bitmap>?>(null, bytes) {
        value = withContext(Dispatchers.Default) {
            runCatching { requireNotNull(decodeBoundedBitmap(bytes)) { "Android could not decode this image" } }
        }
    }
    Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
        when {
            result == null -> CircularProgressIndicator()
            result?.isSuccess == true -> Image(
                result!!.getOrThrow().asImageBitmap(),
                name,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            else -> PreviewFailure(result?.exceptionOrNull()?.message ?: "Android could not decode this image")
        }
    }
}

@Composable
private fun PdfPreview(bytes: ByteArray) {
    val context = LocalContext.current
    var page by rememberSaveable(bytes.contentHashCode()) { mutableStateOf(0) }
    val rendered by produceState<Result<PdfPage>?>(null, bytes, page) {
        value = withContext(Dispatchers.IO) { runCatching { renderPdfPage(context.cacheDir, bytes, page) } }
    }
    Column(Modifier.fillMaxSize()) {
        when {
            rendered == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            rendered?.isFailure == true -> PreviewFailure(rendered?.exceptionOrNull()?.message ?: "Android could not render this PDF")
            else -> rendered!!.getOrThrow().let { result ->
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = { page-- }, enabled = page > 0) { Text("Previous") }
                    Text("${page + 1} / ${result.pageCount}", Modifier.padding(horizontal = 14.dp))
                    OutlinedButton(onClick = { page++ }, enabled = page + 1 < result.pageCount) { Text("Next") }
                }
                Image(
                    result.bitmap.asImageBitmap(),
                    "PDF page ${page + 1}",
                    Modifier.fillMaxSize().padding(8.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun PreviewFailure(message: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("PREVIEW UNAVAILABLE", style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SandboxedHtmlPreview(source: String) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportMultipleWindows(false)
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.safeBrowsingEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse =
                        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(byteArrayOf()))
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, sandboxedHtml(source), "text/html", "utf-8", null)
        },
        onRelease = { webView -> webView.stopLoading(); webView.destroy() },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun createDocumentIntent(entry: ManagedFileEntry): Intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
    addCategory(Intent.CATEGORY_OPENABLE)
    type = entry.mimeType ?: "application/octet-stream"
    putExtra(Intent.EXTRA_TITLE, entry.name)
}

private fun decodeBoundedBitmap(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (
        bounds.outWidth / sample > MAX_IMAGE_DIMENSION ||
        bounds.outHeight / sample > MAX_IMAGE_DIMENSION ||
        (bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample) > MAX_IMAGE_PIXELS
    ) {
        sample *= 2
    }
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )
}

private fun renderPdfPage(cacheDir: File, bytes: ByteArray, requestedPage: Int): PdfPage {
    val file = File.createTempFile("hermes-preview-", ".pdf", cacheDir)
    try {
        file.writeBytes(bytes)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                require(renderer.pageCount > 0) { "This PDF has no renderable pages" }
                val index = requestedPage.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(index).use { page ->
                    val widthScale = MAX_PDF_RENDER_WIDTH.toFloat() / page.width.toFloat()
                    val heightScale = MAX_PDF_RENDER_HEIGHT.toFloat() / page.height.toFloat()
                    val pixelScale = kotlin.math.sqrt(MAX_PDF_RENDER_PIXELS.toDouble() / (page.width.toLong() * page.height.toLong())).toFloat()
                    val scale = minOf(widthScale, heightScale, pixelScale, 2f)
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return PdfPage(bitmap, renderer.pageCount)
                }
            }
        }
    } finally {
        file.delete()
    }
}

private fun sandboxedHtml(source: String): String = """
    <!doctype html><html><head><meta charset="utf-8">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline'; font-src data:">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <style>html{color-scheme:light dark}body{font-family:system-ui,sans-serif;padding:16px;overflow-wrap:anywhere}img{max-width:100%;height:auto}pre{white-space:pre-wrap}</style>
    </head><body>$source</body></html>
""".trimIndent()

private fun Long.formatBytes(): String = when {
    this < 1_024 -> "$this B"
    this < 1_024L * 1_024L -> "%.1f KB".format(this / 1_024.0)
    this < 1_024L * 1_024L * 1_024L -> "%.1f MB".format(this / (1_024.0 * 1_024.0))
    else -> "%.1f GB".format(this / (1_024.0 * 1_024.0 * 1_024.0))
}

private data class PdfPage(val bitmap: Bitmap, val pageCount: Int)

private const val MAX_IMAGE_DIMENSION = 4_096
private const val MAX_IMAGE_PIXELS = 12_000_000L
private const val MAX_PDF_RENDER_WIDTH = 1_600
private const val MAX_PDF_RENDER_HEIGHT = 3_200
private const val MAX_PDF_RENDER_PIXELS = 4_000_000L
