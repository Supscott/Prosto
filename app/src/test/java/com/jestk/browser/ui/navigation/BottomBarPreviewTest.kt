package com.jestk.browser.ui.navigation

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LiquidSurface
import com.jestk.browser.ui.theme.LiquidSurfaceContainerHigh
import com.jestk.browser.ui.viewmodel.BrowserUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BottomBarPreviewTest {

    @Composable
    private fun MockWebPageContent() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("with the official Wikipedia app.", color = Color(0xFF54595D), fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E1B1C),
                    modifier = Modifier.size(width = 140.dp, height = 40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("GET IT ON Google Play", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E1B1C),
                    modifier = Modifier.size(width = 140.dp, height = 40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Download on App Store", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
            Text("Wikipedia is hosted by the Wikimedia Foundation.", color = Color(0xFF72777D), fontSize = 12.sp)
        }
    }

    @Test
    fun renderSimulatedScreen() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val sampleState = BrowserUiState(
            currentUrl = "https://wikipedia.org",
            pageTitle = "Wikipedia",
            tabs = listOf(
                Tab(id = "1", url = "https://wikipedia.org", title = "Wikipedia", isActive = true),
                Tab(id = "2", url = "https://youtube.com", title = "YouTube", isActive = false)
            ),
            activeTabId = "1"
        )

        val bottomBgDark = Color(0xFF161314)

        val composeView = ComposeView(activity).apply {
            setContent {
                JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                    // Simulated device screen in Dark Theme with webpage behind it
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E1B1C))
                    ) {
                        // Webpage behind
                        MockWebPageContent()

                        // Actual UnifiedBottomBar
                        UnifiedBottomBar(
                            uiState = sampleState,
                            onEvent = {},
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

                        // Android system gesture bar indicator simulation at bottom
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                                .size(width = 120.dp, height = 4.dp)
                                .background(Color(0xFF8E8E93), RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }

        activity.setContentView(composeView)
        val width = 1080
        val height = 480
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/bottom_bar_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
