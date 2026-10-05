package com.jestk.browser.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong

object SnapshotEngine {

    private val requestIdGenerator = AtomicLong(0)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var applicationContext: Context? = null

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    fun deleteTabSnapshot(tabId: String) {
        applicationContext?.let { ctx ->
            deleteTabSnapshot(ctx, tabId)
        }
    }

    fun deleteTabSnapshot(context: Context, tabId: String) {
        init(context)
        try {
            val snapshotsDir = File(context.applicationContext.filesDir, "snapshots")
            val targetFile = File(snapshotsDir, "tab_${tabId}.jpg")
            if (targetFile.exists()) {
                targetFile.delete()
            }
        } catch (_: Exception) {}
    }

    fun deleteAllSnapshots(context: Context? = applicationContext) {
        context?.let { ctx ->
            try {
                val snapshotsDir = File(ctx.applicationContext.filesDir, "snapshots")
                if (snapshotsDir.exists()) {
                    snapshotsDir.listFiles()?.forEach { it.delete() }
                }
            } catch (_: Exception) {}
        }
    }

    fun captureSnapshot(
        webView: WebView,
        tabId: String,
        context: Context,
        onVisualColorsSampled: ((topColor: Int, bottomColor: Int) -> Unit)? = null,
        onComplete: (String?) -> Unit
    ) {
        init(context)
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
        val appContext = context.applicationContext
        val width = webView.width.coerceAtLeast(1)
        val height = webView.height.coerceAtLeast(1)

        val targetWidth = 360
        val scale = targetWidth.toFloat() / width
        val targetHeight = (height * scale).toInt().coerceAtLeast(targetWidth)

        val thumbnailBitmap = try {
            Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also { bmp ->
                val canvas = Canvas(bmp)
                canvas.scale(scale, scale)
                webView.draw(canvas)
            }
        } catch (e: Throwable) {
            onComplete(null)
            return
        }

        coroutineScope.launch {
            if (onVisualColorsSampled != null && targetWidth > 0 && targetHeight > 0) {
                try {
                    val topY = (10 * scale).toInt().coerceIn(0, targetHeight - 1)
                    val bottomY = ((height - 10) * scale).toInt().coerceIn(0, targetHeight - 1)
                    val centerX = targetWidth / 2
                    val rawTop = thumbnailBitmap.getPixel(centerX, topY)
                    val rawBottom = thumbnailBitmap.getPixel(centerX, bottomY)
                    if (rawTop != 0) {
                        val topColor = rawTop or (0xFF shl 24)
                        val bottomColor = rawBottom or (0xFF shl 24)
                        mainHandler.post {
                            onVisualColorsSampled(topColor, bottomColor)
                        }
                    }
                } catch (_: Exception) {}
            }

            try {
                val snapshotsDir = File(appContext.filesDir, "snapshots").apply {
                    if (!exists()) mkdirs()
                }
                val targetFile = File(snapshotsDir, "tab_${tabId}.jpg")

                FileOutputStream(targetFile).use { out ->
                    thumbnailBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                thumbnailBitmap.recycle()

                mainHandler.post { onComplete(targetFile.absolutePath) }
            } catch (e: Exception) {
                try { thumbnailBitmap.recycle() } catch (_: Throwable) {}
                mainHandler.post { onComplete(null) }
            }
        }
    }
}
