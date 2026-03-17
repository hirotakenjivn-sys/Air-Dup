package com.datatrans.server

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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

        if (isCaptivePortalCheck(uri, host)) {
            return redirectToMainPage()
        }

        return when {
            uri == "/" || uri == "" -> serveMainPage()
            uri.startsWith("/thumb/") -> serveThumb(uri.removePrefix("/thumb/").toIntOrNull())
            uri.startsWith("/download/") -> serveFile(uri.removePrefix("/download/").toIntOrNull())
            uri == "/text" -> serveTextPage()
            else -> redirectToMainPage()
        }
    }

    private fun isCaptivePortalCheck(uri: String, host: String): Boolean {
        if (uri.contains("hotspot-detect") || host.contains("captive.apple.com")) return true
        if (uri.contains("generate_204") || host.contains("connectivitycheck")) return true
        if (uri.contains("connecttest") || host.contains("msftconnecttest")) return true
        if (uri.contains("ncsi") || host.contains("msftncsi")) return true
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
        val imagePreviews = sharedFiles.mapIndexed { index, file ->
            if (file.mimeType.startsWith("image/")) {
                """<div class="img-wrap">
                    <img src="/thumb/$index" data-full="/download/$index" loading="lazy">
                </div>"""
            } else ""
        }.joinToString("\n")

        val textSection = if (sharedText != null) {
            """<div class="text-card">
                <div class="text-content" id="textContent">${escapeHtml(sharedText!!)}</div>
                <div class="copied-msg" id="copiedMsg"></div>
            </div>"""
        } else ""

        val imageCount = sharedFiles.count { it.mimeType.startsWith("image/") }

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
            margin-bottom: 8px;
            background: linear-gradient(135deg, #60a5fa, #a78bfa);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .hint {
            text-align: center;
            font-size: 14px;
            color: #60a5fa;
            margin-bottom: 20px;
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
            aspect-ratio: 1;
        }
        .img-wrap img {
            width: 100%;
            height: 100%;
            object-fit: cover;
            display: block;
            -webkit-touch-callout: default;
        }
        .fullscreen-overlay {
            display: none;
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(0,0,0,0.95);
            z-index: 100;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 20px;
        }
        .fullscreen-overlay.active { display: flex; }
        .fullscreen-overlay img {
            max-width: 100%;
            max-height: 70vh;
            object-fit: contain;
            border-radius: 8px;
            -webkit-touch-callout: default;
        }
        .fullscreen-hint {
            color: #60a5fa;
            font-size: 16px;
            margin-top: 16px;
            text-align: center;
        }
        .fullscreen-close {
            position: absolute;
            top: 16px;
            right: 16px;
            background: rgba(255,255,255,0.2);
            border: none;
            color: #fff;
            font-size: 24px;
            width: 44px;
            height: 44px;
            border-radius: 22px;
            cursor: pointer;
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
    <div class="hint">${imageCount}件の画像 — タップして拡大 → 長押しで写真に保存</div>
    <div class="images">$imagePreviews</div>
    $textSection

    <div class="fullscreen-overlay" id="overlay" onclick="closeOverlay(event)">
        <button class="fullscreen-close" onclick="closeOverlay(event)">✕</button>
        <img id="fullImg" src="">
        <div class="fullscreen-hint">画像を長押し →「写真に追加」で保存</div>
    </div>

    <script>
        // Tap thumbnail to open fullscreen with full-res image
        document.querySelectorAll('.img-wrap img').forEach(img => {
            img.addEventListener('click', () => {
                const src = img.getAttribute('data-full');
                document.getElementById('fullImg').src = src;
                document.getElementById('overlay').classList.add('active');
            });
        });

        function closeOverlay(e) {
            if (e.target.id === 'overlay' || e.target.classList.contains('fullscreen-close')) {
                document.getElementById('overlay').classList.remove('active');
            }
        }

        // Auto-copy text
        const textEl = document.getElementById('textContent');
        if (textEl) {
            textEl.addEventListener('click', () => {
                navigator.clipboard.writeText(textEl.innerText).then(() => {
                    document.getElementById('copiedMsg').textContent = 'コピーしました ✓';
                });
            });
            navigator.clipboard.writeText(textEl.innerText).then(() => {
                document.getElementById('copiedMsg').textContent = 'クリップボードにコピー済み ✓';
            }).catch(() => {});
        }
    </script>
</body>
</html>"""

        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, html)
    }

    private fun serveThumb(index: Int?): Response {
        if (index == null || index !in sharedFiles.indices) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, "Not found")
        }

        val file = sharedFiles[index]
        if (!file.mimeType.startsWith("image/")) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, "Not an image")
        }

        return try {
            val inputStream = context.contentResolver.openInputStream(file.uri)
                ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_HTML, "Cannot read")

            // Decode with sample size for thumbnail (max 300px)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            val tempStream = context.contentResolver.openInputStream(file.uri)!!
            BitmapFactory.decodeStream(tempStream, null, options)
            tempStream.close()

            val maxDim = maxOf(options.outWidth, options.outHeight)
            val sampleSize = maxOf(1, maxDim / 300)

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            inputStream.close()

            if (bitmap == null) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_HTML, "Decode failed")
            }

            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
            bitmap.recycle()
            val thumbBytes = out.toByteArray()

            val response = newFixedLengthResponse(
                Response.Status.OK,
                "image/jpeg",
                ByteArrayInputStream(thumbBytes),
                thumbBytes.size.toLong()
            )
            response.addHeader("Cache-Control", "max-age=3600")
            response
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_HTML, "Error: ${e.message}")
        }
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
            // Inline display for images (long-press to save to camera roll)
            if (file.mimeType.startsWith("image/")) {
                response.addHeader("Content-Disposition", "inline; filename=\"${file.name}\"")
            } else {
                response.addHeader("Content-Disposition", "attachment; filename=\"${file.name}\"")
            }
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

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes} B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    }
}
