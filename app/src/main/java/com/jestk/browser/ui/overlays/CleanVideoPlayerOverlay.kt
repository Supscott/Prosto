package com.jestk.browser.ui.overlays

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jestk.browser.R
import com.jestk.browser.util.YouTubeUrlHelper

private class CleanPlayerBridge(
    private val onPlaybackError: (Int) -> Unit,
) {
    @JavascriptInterface
    fun onPlaybackError(errorCode: Int) {
        Handler(Looper.getMainLooper()).post {
            onPlaybackError.invoke(errorCode)
        }
    }
}

/**
 * An immersive, distraction-free player overlay for YouTube videos.
 *
 * Runs `youtube-nocookie.com/embed/<id>?autoplay=1` inside a privacy-enhanced HTML5 container
 * loaded with a strict-origin referrer policy to completely prevent Error 153 and ad-watchdog buffering.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CleanVideoPlayerOverlay(
    videoId: String,
    originalUrl: String,
    startSeconds: Int = 0,
    onClose: () -> Unit,
    onOpenOnWeb: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler {
        onClose()
    }

    var playerWebView by remember { mutableStateOf<WebView?>(null) }
    var isPlaybackRestricted by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, playerWebView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> playerWebView?.onPause()
                Lifecycle.Event.ON_RESUME -> playerWebView?.onResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            playerWebView?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            playerWebView = null
        }
    }

    Surface(
        color = Color(0xFF0F0F0F),
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.clean_player_close),
                        tint = Color.White,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.clean_player_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = stringResource(R.string.clean_player_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                FilledTonalButton(
                    onClick = { onOpenOnWeb(originalUrl) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF27272A),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.clean_player_open_on_site),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            // Video Player Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            setBackgroundColor(android.graphics.Color.BLACK)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true

                            try {
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            } catch (_: Throwable) {}

                            val bridge = CleanPlayerBridge { errorCode ->
                                if (errorCode == 101 || errorCode == 150 || errorCode == 153) {
                                    isPlaybackRestricted = true
                                }
                            }
                            addJavascriptInterface(bridge, "CleanPlayerBridge")

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    val clickUrl = request?.url?.toString().orEmpty()
                                    if (clickUrl.isNotEmpty() && !clickUrl.contains("youtube-nocookie.com/embed")) {
                                        onOpenOnWeb(clickUrl)
                                        return true
                                    }
                                    return false
                                }
                            }

                            webChromeClient = object : WebChromeClient() {}

                            val embedHtml = YouTubeUrlHelper.buildEmbedHtml(videoId, startSeconds)
                            loadDataWithBaseURL("https://www.youtube-nocookie.com", embedHtml, "text/html", "UTF-8", null)
                            playerWebView = this
                        }
                    },
                    update = { wv ->
                        // If videoId changes while open
                        val currentTag = wv.tag as? String
                        if (currentTag != videoId) {
                            wv.tag = videoId
                            isPlaybackRestricted = false
                            val newHtml = YouTubeUrlHelper.buildEmbedHtml(videoId, startSeconds)
                            wv.loadDataWithBaseURL("https://www.youtube-nocookie.com", newHtml, "text/html", "UTF-8", null)
                        }
                    },
                )

                // Restriction notification fallback
                if (isPlaybackRestricted) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1E293B),
                        ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(36.dp),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.clean_player_restricted_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.clean_player_restricted_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFCBD5E1),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { onOpenOnWeb(originalUrl) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEF4444),
                                    contentColor = Color.White,
                                ),
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = stringResource(R.string.clean_player_open_on_site))
                            }
                        }
                    }
                }
            }
        }
    }
}
