package com.datatrans.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datatrans.UiState

@Composable
fun SendScreen(
    uiState: UiState,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "送信中",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onStop) {
                Icon(Icons.Default.Close, contentDescription = "停止", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Step 1: WiFi QR
        StepCard(step = "1", title = "WiFiに接続") {
            uiState.wifiQrBitmap?.let { QrImage(it) }
            uiState.hotspotInfo?.let { info ->
                Spacer(modifier = Modifier.height(8.dp))
                Text("SSID: ${info.ssid}", fontSize = 12.sp, color = Color.Gray)
                Text("Pass: ${info.password}", fontSize = 12.sp, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Step 2: Download QR
        StepCard(step = "2", title = "このQRでダウンロード") {
            uiState.urlQrBitmap?.let { QrImage(it) }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.downloadUrl,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Download count
        if (uiState.downloadCount > 0) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F2A1A))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "${uiState.downloadCount}件ダウンロード済み",
                        fontSize = 14.sp,
                        color = Color(0xFF4ADE80)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Info
        if (uiState.selectedFileNames.isNotEmpty()) {
            Text(
                "${uiState.selectedFileNames.size}件の画像を共有中",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
        if (uiState.sharedText != null) {
            Text("テキストも共有中", fontSize = 12.sp, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("共有を停止")
        }
    }
}

@Composable
private fun StepCard(
    step: String,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(step, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun QrImage(bitmap: Bitmap) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "QR Code",
        modifier = Modifier
            .size(220.dp)
            .clip(RoundedCornerShape(12.dp))
    )
}
