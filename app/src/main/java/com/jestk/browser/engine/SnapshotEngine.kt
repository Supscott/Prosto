package com.jestk.browser.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong

object SnapshotEngine {

    private val requestIdGenerator = AtomicLong(0)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun captureSnapshot(
        webView: WebView,
        tabId: String,
        context: Context,
        onVisualColorsSampled: ((topColor: Int, bottomColor: Int) -> Unit)? = null,
        onComplete: (String?) -> Unit
    ) {
        val width = webView.width
        val height = webView.height

        if (width <= 0 || height <= 0) {
            onComplete(null)
            return
        }

        val requestId = requestIdGenerator.incrementAndGet()

        // Use postVisualStateCallback so Blink has rendered pending frames before drawing
        try {
            webView.postVisualStateCallback(requestId, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    processCanvasCapture(webView, tabId, context, onVisualColorsSampled, onComplete)
                }
            })
        } catch (e: Exception) {
            // Fallback with small delay if visual state callback throws
            mainHandler.postDelayed({
                processCanvasCapture(webView, tabId, context, onVisualColorsSampled, onComplete)
            }, 100)
        }
    }

    private fun processCanvasCapture(
        webView: WebView,
        tabId: String,
        context: Context,
        onVisualColorsSampled: ((topColor: Int, bottomColor: Int) -> Unit)?,
        onComplete: (String?) -> Unit
    ) {
        try {
            val width = webView.width.coerceAtLeast(1)
            val height = webView.height.coerceAtLeast(1)

            val fullBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(fullBitmap)
            webView.draw(canvas)

            if (onVisualColorsSampled != null && width > 0 && height > 0) {
                try {
                    val topY = 10.coerceAtMost(height - 1)
                    val bottomY = (height - 10).coerceAtLeast(0)
                    val centerX = width / 2
                    val rawTop = fullBitmap.getPixel(centerX, topY)
                    val rawBottom = fullBitmap.getPixel(centerX, bottomY)
                    if (rawTop != 0) {
                        val topColor = rawTop or (0xFF shl 24)
                        val bottomColor = rawBottom or (0xFF shl 24)
                        mainHandler.post {
                            onVisualColorsSampled(topColor, bottomColor)
                        }
                    }
                } catch (_: Exception) {}
            }

            // Scale down to thumbnail size for high performance in 3-column Tab Grid
            val targetWidth = 360
            val targetHeight = (height * (targetWidth.toFloat() / width)).toInt().coerceAtLeast(targetWidth)

            val scaledBitmap = Bitmap.createScaledBitmap(fullBitmap, targetWidth, targetHeight, true)
            if (scaledBitmap != fullBitmap) {
                fullBitmap.recycle()
            }

            val snapshotsDir = File(context.filesDir, "snapshots").apply {
                if (!exists()) mkdirs()
            }
            val targetFile = File(snapshotsDir, "tab_${tabId}.jpg")

            FileOutputStream(targetFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            scaledBitmap.recycle()

            onComplete(targetFile.absolutePath)
        } catch (e: Exception) {
            onComplete(null)
        }
    }
}
