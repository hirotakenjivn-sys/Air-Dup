package com.datatrans

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import com.datatrans.hotspot.HotspotInfo
import com.datatrans.hotspot.HotspotManager
import com.datatrans.qr.QrCodeGenerator
import com.datatrans.server.LocalWebServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppState {
    IDLE,
    STARTING,
    READY,
    ERROR
}

data class UiState(
    val appState: AppState = AppState.IDLE,
    val hotspotInfo: HotspotInfo? = null,
    val wifiQrBitmap: Bitmap? = null,
    val urlQrBitmap: Bitmap? = null,
    val downloadUrl: String = "",
    val selectedFileNames: List<String> = emptyList(),
    val sharedText: String? = null,
    val errorMessage: String? = null,
    val downloadCount: Int = 0
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val hotspotManager = HotspotManager(application)
    private var webServer: LocalWebServer? = null

    private val serverPort = 8080

    fun startSharing(fileUris: List<Uri>, text: String?) {
        _uiState.value = _uiState.value.copy(appState = AppState.STARTING, errorMessage = null)

        val context = getApplication<Application>()

        // Prepare shared files
        val sharedFiles = fileUris.mapNotNull { uri ->
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIndex >= 0) it.getString(nameIndex) else "file"
                    val size = if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L
                    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    LocalWebServer.SharedFile(name, mimeType, uri, size)
                } else null
            }
        }

        // Start hotspot
        hotspotManager.start { result ->
            result.onSuccess { info ->
                // Start web server
                val server = LocalWebServer(context, serverPort)
                server.setSharedFiles(sharedFiles)
                server.setSharedText(text)
                server.onDownloadCompleted = {
                    _uiState.value = _uiState.value.copy(
                        downloadCount = _uiState.value.downloadCount + 1
                    )
                }
                server.start()
                webServer = server

                val downloadUrl = "http://${info.ipAddress}:$serverPort"

                // Generate QR codes
                val wifiQr = QrCodeGenerator.generateWifiQr(info.ssid, info.password)
                val urlQr = QrCodeGenerator.generateUrlQr(downloadUrl)

                _uiState.value = _uiState.value.copy(
                    appState = AppState.READY,
                    hotspotInfo = info,
                    wifiQrBitmap = wifiQr,
                    urlQrBitmap = urlQr,
                    downloadUrl = downloadUrl,
                    selectedFileNames = sharedFiles.map { it.name },
                    sharedText = text
                )
            }
            result.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    appState = AppState.ERROR,
                    errorMessage = e.message ?: "ホットスポットの起動に失敗しました"
                )
            }
        }
    }

    fun stopSharing() {
        webServer?.stop()
        webServer = null
        hotspotManager.stop()
        _uiState.value = UiState()
    }

    override fun onCleared() {
        super.onCleared()
        stopSharing()
    }
}
