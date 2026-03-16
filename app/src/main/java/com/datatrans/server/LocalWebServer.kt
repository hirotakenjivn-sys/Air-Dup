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
    var onDownloadStarted: (() -> Unit)? = null
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

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        return when {
            uri == "/" || uri == "" -> serveMainPage()
            uri.startsWith("/download/") -> serveFile(uri.removePrefix("/download/").toIntOrNull())
            uri == "/text" -> serveTextPage()
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, "Not found")
        }
    }

    private fun serveMainPage(): Response {
        val fileLinks = sharedFiles.mapIndexed { index, file ->
            """<a href="/download/$index" class="file-card">
                <div class="icon">📷</div>
                <div class="name">${escapeHtml(file.name)}</div>
                <div class="size">${formatSize(file.size)}</div>
            </a>"""
        }.joinToString("\n")

        val textSection = if (sharedText != null) {
            """<div class="text-card">
                <div class="label">共有テキスト</div>
                <div class="text-content" id="textContent">${escapeHtml(sharedText!!)}</div>
                <button onclick="copyText()" class="copy-btn">コピー</button>
            </div>"""
        } else ""

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
            font-size: 24px;
            margin-bottom: 8px;
            background: linear-gradient(135deg, #60a5fa, #a78bfa);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .subtitle {
            text-align: center;
            color: #666;
            font-size: 14px;
            margin-bottom: 32px;
        }
        .files {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
            gap: 12px;
            margin-bottom: 24px;
        }
        .file-card {
            display: flex;
            flex-direction: column;
            align-items: center;
            padding: 20px 12px;
            background: #1a1a1a;
            border-radius: 16px;
            text-decoration: none;
            color: #fff;
            transition: transform 0.15s, background 0.15s;
        }
        .file-card:active { transform: scale(0.95); background: #252525; }
        .icon { font-size: 40px; margin-bottom: 8px; }
        .name { font-size: 13px; text-align: center; word-break: break-all; }
        .size { font-size: 11px; color: #888; margin-top: 4px; }
        .text-card {
            background: #1a1a1a;
            border-radius: 16px;
            padding: 20px;
            margin-bottom: 24px;
        }
        .label { font-size: 12px; color: #888; margin-bottom: 8px; }
        .text-content {
            font-size: 16px;
            line-height: 1.5;
            margin-bottom: 12px;
            white-space: pre-wrap;
            word-break: break-all;
        }
        .copy-btn {
            background: #60a5fa;
            color: #000;
            border: none;
            border-radius: 12px;
            padding: 10px 24px;
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
            width: 100%;
        }
        .copy-btn:active { opacity: 0.8; }
    </style>
</head>
<body>
    <h1>DataTrans</h1>
    <div class="subtitle">タップしてダウンロード</div>
    <div class="files">$fileLinks</div>
    $textSection
    <script>
        function copyText() {
            const text = document.getElementById('textContent').innerText;
            navigator.clipboard.writeText(text).then(() => {
                const btn = document.querySelector('.copy-btn');
                btn.textContent = 'コピーしました ✓';
                setTimeout(() => btn.textContent = 'コピー', 2000);
            });
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
        onDownloadStarted?.invoke()

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

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    }
}
