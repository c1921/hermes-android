package com.nousresearch.hermes.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nousresearch.hermes.data.BackendConfig
import com.nousresearch.hermes.data.WorkspaceFilePreview
import com.nousresearch.hermes.data.WorkspaceFilesRepository
import com.nousresearch.hermes.protocol.ManagedFileEntry
import com.nousresearch.hermes.protocol.ManagedFilesResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkspaceFilesUiState(
    val listing: ManagedFilesResponse? = null,
    val preview: WorkspaceFilePreview? = null,
    val loading: Boolean = false,
    val previewLoading: Boolean = false,
    val downloading: ManagedFileEntry? = null,
    val downloadProgress: Float? = null,
    val notice: String? = null,
    val error: String? = null,
)

@HiltViewModel
class WorkspaceFilesViewModel @Inject constructor(
    private val files: WorkspaceFilesRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WorkspaceFilesUiState())
    val state = mutableState.asStateFlow()

    private var backend: BackendConfig? = null
    private var loadJob: Job? = null
    private var previewJob: Job? = null
    private var downloadJob: Job? = null

    fun bind(config: BackendConfig, initialPath: String?) {
        if (backend?.id != config.id) {
            loadJob?.cancel()
            previewJob?.cancel()
            downloadJob?.cancel()
            backend = config
            mutableState.value = WorkspaceFilesUiState()
        }
        if (mutableState.value.listing == null && !mutableState.value.loading) {
            load(initialPath, fallbackToManagedRoot = !initialPath.isNullOrBlank())
        }
    }

    fun open(path: String?) = load(path, fallbackToManagedRoot = false)

    private fun load(path: String?, fallbackToManagedRoot: Boolean) {
        val config = backend ?: return
        loadJob?.cancel()
        previewJob?.cancel()
        loadJob = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, preview = null, previewLoading = false, error = null, notice = null) }
            try {
                val listing = try {
                    files.list(config, path)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (initialError: Throwable) {
                    if (!fallbackToManagedRoot) throw initialError
                    files.list(config, null)
                }
                mutableState.update {
                    it.copy(
                        listing = listing,
                        loading = false,
                        notice = if (fallbackToManagedRoot && listing.path != path) {
                            "The session workspace is unavailable. Showing the Hermes managed root."
                        } else {
                            null
                        },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update { it.copy(loading = false, error = error.userMessage()) }
            }
        }
    }

    fun refresh() = open(mutableState.value.listing?.path)

    fun preview(entry: ManagedFileEntry) {
        val config = backend ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            mutableState.update { it.copy(previewLoading = true, error = null, notice = null) }
            try {
                val preview = files.preview(config, entry)
                mutableState.update { it.copy(preview = preview, previewLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update { it.copy(previewLoading = false, error = error.userMessage()) }
            }
        }
    }

    fun closePreview() {
        previewJob?.cancel()
        mutableState.update { it.copy(preview = null, previewLoading = false) }
    }

    fun download(entry: ManagedFileEntry, destination: Uri) {
        val config = backend ?: return
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            mutableState.update { it.copy(downloading = entry, downloadProgress = 0f, error = null, notice = null) }
            try {
                files.download(config, entry, destination) { copied, total ->
                    mutableState.update { current ->
                        current.copy(downloadProgress = total?.takeIf { it > 0 }?.let { copied.toFloat() / it.toFloat() })
                    }
                }
                mutableState.update { it.copy(downloading = null, downloadProgress = null, notice = "Saved ${entry.name}") }
            } catch (cancelled: CancellationException) {
                mutableState.update { it.copy(downloading = null, downloadProgress = null, notice = "Download cancelled") }
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(downloading = null, downloadProgress = null, error = "Download failed: ${error.userMessage()}")
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }
}

private fun Throwable.userMessage(): String = message?.trim().takeUnless { it.isNullOrBlank() }
    ?: "Hermes file operation failed"
