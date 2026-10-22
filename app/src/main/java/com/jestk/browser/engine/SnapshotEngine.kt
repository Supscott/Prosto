package com.jestk.browser.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong

object SnapshotEngine {

    private const val TAG = "SnapshotEngine"

    private val requestIdGenerator = AtomicLong(0)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var applicationContext: Context? = null

    private val scopeLock = Any()

    @Volatile
    private var coroutineScope: CoroutineScope = createScope()

    private fun createScope(): CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Returns the scope that runs the snapshot work, recreating it when it was cancelled by
     * [shutdown].
     *
     * The engine is an app-scoped singleton: a shutdown must never leave it unable to capture
     * snapshots, so the scope is recreated lazily instead of staying dead.
     */
    private fun activeScope(): CoroutineScope =
        synchronized(scopeLock) {
            val current = coroutineScope
            if (current.coroutineContext[Job]?.isActive == true) {
                current
            } else {
                createScope().also { coroutineScope = it }
            }
        }

    /**
     * Reports whether the background scope of the engine is running.
     * Used by the tests to verify the teardown contract of [shutdown].
     */
    internal fun isScopeActive(): Boolean = coroutineScope.coroutineContext[Job]?.isActive == true

    /**
     * Makes sure the engine owns a running scope, recreating it after a [shutdown].
     * Used by the tests to verify the teardown contract of [shutdown].
     */
    internal fun ensureScope(): Boolean = activeScope().coroutineContext[Job]?.isActive == true

    /**
     * Cancels the pending snapshot work and releases the scope.
     *
     * The call is idempotent and safe for a live app: the scope is recreated lazily on the next
     * capture, so the engine keeps working after a shutdown.
     */
    fun shutdown() {
        synchronized(scopeLock) {
            coroutineScope.cancel()
        }
    }

    /**
     * Stores the application context used to resolve the snapshot directory.
     *
     * The method is called on every capture, so it must not cancel the running scope: the scope
     * is only replaced when it was already cancelled by [shutdown], which keeps the scope from
     * leaking when the engine is initialised more than once.
     */
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
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete snapshot for tab $tabId", e)
        }
    }

    fun deleteAllSnapshots(context: Context? = applicationContext) {
        context?.let { ctx ->
            try {
                val snapshotsDir = File(ctx.applicationContext.filesDir, "snapshots")
                if (snapshotsDir.exists()) {
                    snapshotsDir.listFiles()?.forEach { it.delete() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete all snapshots", e)
            }
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
            Log.w(TAG, "Visual state callback failed for tab $tabId, retrying with delay", e)
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
            Log.w(TAG, "Failed to capture canvas for tab $tabId", e)
            onComplete(null)
            return
        }

        activeScope().launch {
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
                } catch (e: Exception) {
                    Log.d(TAG, "Best-effort color sampling failed for tab $tabId", e)
                }
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

                Log.d(TAG, "Captured snapshot for tab $tabId")
                mainHandler.post { onComplete(targetFile.absolutePath) }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write snapshot file for tab $tabId", e)
                try { thumbnailBitmap.recycle() } catch (recycleError: Throwable) {
                    Log.d(TAG, "Best-effort bitmap recycle failed for tab $tabId", recycleError)
                }
                mainHandler.post { onComplete(null) }
            }
        }
    }
}
