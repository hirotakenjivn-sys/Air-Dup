package com.datatrans.server

import android.net.Uri
import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream

class LocalWebServer(
    private val context: Context,
    port: Int = 8080
) : NanoHTTPD(port) {

    private var sharedFiles: List<SharedFile> = emptyList()
    private var sharedText: String? = null
    private var serverIp: String = "192.168.49.1"
    var onDownloadCompleted: (() -> Unit)? = null

    data class SharedFile(
        val name: String,
        val mimeType: String,
        val uri: Uri,
        val size: Long
    )

    fun setSharedFiles(files: List<SharedFile>) {
        sharedFiles = files
    }

    fun setSharedText(text: String?) {
        sharedText = text
    }

    fun setServerIp(ip: String) {
        serverIp = ip
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val host = session.headers["host"] ?: ""

        // Captive portal detection: redirect any non-local requests to our page
        // iOS: /hotspot-detect.html, captive.apple.com
        // Android: /generate_204, connectivitycheck.gstatic.com
        // Windows: /connecttest.txt, msftconnecttest.com
        if (isCaptivePortalCheck(uri, host)) {
            return redirectToMainPage()
        }

        return when {
            uri == "/" || uri == "" -> serveMainPage()
            uri.startsWith("/download/") -> serveFile(uri.removePrefix("/download/").toIntOrNull())
            uri == "/text" -> serveTextPage()
            else -> redirectToMainPage() // Unknown paths -> redirect to main
        }
    }

    private fun isCaptivePortalCheck(uri: String, host: String): Boolean {
        // iOS
        if (uri.contains("hotspot-detect") || host.contains("captive.apple.com")) return true
        // Android
        if (uri.contains("generate_204") || host.contains("connectivitycheck")) return true
        // Windows
        if (uri.contains("connecttest") || host.contains("msftconnecttest")) return true
        // Generic
        if (uri.contains("ncsi") || host.contains("msftncsi")) return true
        // If the host is not our server IP, it's a captive portal check
        if (host.isNotEmpty() && !host.startsWith(serverIp) && !host.startsWith("localhost")) return true
        return false
    }

    private fun redirectToMainPage(): Response {
        val response = newFixedLengthResponse(
            Response.Status.REDIRECT,
            MIME_HTML,
            "<html><body>Redirecting...</body></html>"
        )
        response.addHeader("Location", "http://$serverIp:8080/")
        return response
    }

    private fun serveMainPage(): Response {
        val imageCount = sharedFiles.count { it.mimeType.startsWith("image/") }

        val imagePreviews = sharedFiles.mapIndexed { index, file ->
            if (file.mimeType.startsWith("image/")) {
                """<div class="img-wrap">
                    <img src="/download/$index" alt="${escapeHtml(file.name)}">
                </div>"""
            } else ""
        }.joinToString("\n")

        val textSection = if (sharedText != null) {
            """<div class="text-card">
                <div class="text-content" id="textContent">${escapeHtml(sharedText!!)}</div>
                <div class="copied-msg" id="copiedMsg"></div>
            </div>"""
        } else ""

        // Build download links for auto-download JS
        val downloadLinks = sharedFiles.mapIndexed { index, file ->
            """{ url: "/download/$index", name: "${escapeJs(file.name)}" }"""
        }.joinToString(",\n            ")

        val html = """<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>DataTrans</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, sans-serif;
            background: #0a0a0a;
            color: #fff;
            min-height: 100vh;
            padding: 24px 16px;
        }
        h1 {
            text-align: center;
            font-size: 28px;
            margin-bottom: 24px;
            background: linear-gradient(135deg, #60a5fa, #a78bfa);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .status {
            text-align: center;
            font-size: 16px;
            margin-bottom: 24px;
            padding: 16px;
            border-radius: 16px;
            background: #1a1a1a;
        }
        .status.done {
            background: #0f2a1a;
            color: #4ade80;
        }
        .status.saving {
            color: #60a5fa;
        }
        .images {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
            gap: 8px;
            margin-bottom: 24px;
        }
        .img-wrap {
            border-radius: 12px;
            overflow: hidden;
            background: #1a1a1a;
        }
        .img-wrap img {
            width: 100%;
            display: block;
        }
        .text-card {
            background: #1a1a1a;
            border-radius: 16px;
            padding: 20px;
            margin-bottom: 24px;
        }
        .text-content {
            font-size: 16px;
            line-height: 1.5;
            white-space: pre-wrap;
            word-break: break-all;
            cursor: pointer;
        }
        .copied-msg {
            font-size: 13px;
            color: #4ade80;
            margin-top: 8px;
            text-align: center;
        }
    </style>
</head>
<body>
    <h1>DataTrans</h1>
    <div class="status saving" id="status">保存中...</div>
    <div class="images">$imagePreviews</div>
    $textSection
    <script>
        const files = [
            $downloadLinks
        ];

        let completed = 0;

        async function downloadAll() {
            for (const file of files) {
                try {
                    const a = document.createElement('a');
                    a.href = file.url;
                    a.download = file.name;
                    a.style.display = 'none';
                    document.body.appendChild(a);
                    a.click();
                    document.body.removeChild(a);
                    completed++;
                    // Small delay between downloads to avoid browser blocking
                    await new Promise(r => setTimeout(r, 500));
                } catch(e) {
                    console.error('Download failed:', file.name, e);
                }
            }
            const status = document.getElementById('status');
            status.textContent = '全て保存されました ✓ (' + completed + '件)';
            status.className = 'status done';
        }

        // Auto-copy text if present
        const textEl = document.getElementById('textContent');
        if (textEl) {
            textEl.addEventListener('click', () => {
                navigator.clipboard.writeText(textEl.innerText).then(() => {
                    document.getElementById('copiedMsg').textContent = 'コピーしました ✓';
                });
            });
            // Auto-copy on load
            navigator.clipboard.writeText(textEl.innerText).then(() => {
                document.getElementById('copiedMsg').textContent = 'クリップボードにコピー済み ✓';
            }).catch(() => {});
        }

        // Start auto-download after page loads
        if (files.length > 0) {
            downloadAll();
        } else {
            const status = document.getElementById('status');
            if (textEl) {
                status.textContent = 'テキストを共有中';
                status.className = 'status done';
            }
        }
    </script>
</body>
</html>"""

        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, html)
    }

    private fun serveFile(index: Int?): Response {
        if (index == null || index !in sharedFiles.indices) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, "File not found")
        }

        val file = sharedFiles[index]

        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(file.uri)
                ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_HTML, "Cannot read file")

            val response = newFixedLengthResponse(
                Response.Status.OK,
                file.mimeType,
                inputStream,
                file.size
            )
            response.addHeader("Content-Disposition", "attachment; filename=\"${file.name}\"")
            response.addHeader("Cache-Control", "no-cache")
            onDownloadCompleted?.invoke()
            response
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_HTML, "Error: ${e.message}")
        }
    }

    private fun serveTextPage(): Response {
        val text = sharedText ?: "No text shared"
        return newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", text)
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun escapeJs(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    }
}
