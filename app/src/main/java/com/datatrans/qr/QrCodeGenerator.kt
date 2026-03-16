package com.datatrans.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeGenerator {

    fun generateWifiQr(ssid: String, password: String, size: Int = 512): Bitmap {
        val content = "WIFI:T:WPA;S:$ssid;P:$password;;"
        return generate(content, size)
    }

    fun generateUrlQr(url: String, size: Int = 512): Bitmap {
        return generate(url, size)
    }

    private fun generate(content: String, size: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )

        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.WHITE else Color.BLACK)
            }
        }

        return bitmap
    }
}
