package com.jestk.browser.ui.overlays

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.navigation.SearchEngineSwitcherEditPopup
import com.jestk.browser.ui.navigation.SearchEngineSwitcherRow
import com.jestk.browser.ui.screens.SearchPageProvidersDialog
import com.jestk.browser.ui.theme.JestkBrowserTheme
import org.junit.Assert.assertTrue
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
class SearchEngineSwitcherEditPreviewTest {

    private val sampleEngines = listOf(
        SearchEngine.GOOGLE,
        SearchEngine.DUCKDUCKGO,
        SearchEngine.BRAVE,
        SearchEngine(
            id = "yandex",
            displayName = "Yandex",
            searchUrlTemplate = "https://yandex.ru/search/?text=%s",
            iconUrl = "file:///android_asset/icons/yandex.svg",
            shortcut = "y",
            orderPosition = 3
        ),
        SearchEngine(
            id = "yahoo",
            displayName = "Yahoo",
            searchUrlTemplate = "https://search.yahoo.com/search?p=%s",
            iconUrl = "file:///android_asset/icons/yahoo.svg",
            shortcut = "yh",
            orderPosition = 4
        ),
        SearchEngine(
            id = "ecosia",
            displayName = "Ecosia",
            searchUrlTemplate = "https://www.ecosia.org/search?q=%s",
            iconUrl = "file:///android_asset/icons/ecosia.svg",
            shortcut = "e",
            orderPosition = 5
        )
    )

    private fun renderToBitmap(width: Int, height: Int, content: @Composable () -> Unit): Bitmap {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView = ComposeView(activity).apply {
            setContent {
                content()
            }
        }
        activity.setContentView(composeView)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))
        return bitmap
    }

    @Test
    fun renderSearchEngineSwitcherRow_withEditButton() {
        val bitmap = renderToBitmap(width = 412, height = 120) {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF8FAFC))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SearchEngineSwitcherRow(
                        selectedEngine = SearchEngine.GOOGLE,
                        onSelectEngine = {},
                        isDark = false,
                        engines = sampleEngines,
                        onEditClick = {},
                        isEditActive = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        assertThat(bitmap.width).isEqualTo(412)
        assertThat(bitmap.height).isEqualTo(120)

        val outputFile = File("build/search_switcher_row_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(outputFile.exists())
    }

    @Test
    fun renderSearchEngineSwitcherEditPopup_lightAndDark() {
        val bitmap = renderToBitmap(width = 500, height = 400) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A))
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFFF1F5F9))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Edit Popup (Light)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        SearchEngineSwitcherEditPopup(
                            expanded = true,
                            engines = sampleEngines,
                            hiddenEngineIds = setOf(SearchEngine.BRAVE.id),
                            isDark = false,
                            onToggleEngine = { _, _ -> },
                            onDismiss = {}
                        )
                    }
                }

                JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF1E293B))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Edit Popup (Dark)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        SearchEngineSwitcherEditPopup(
                            expanded = true,
                            engines = sampleEngines,
                            hiddenEngineIds = setOf(SearchEngine.BRAVE.id),
                            isDark = true,
                            onToggleEngine = { _, _ -> },
                            onDismiss = {}
                        )
                    }
                }
            }
        }

        val outputFile = File("build/search_switcher_edit_popup_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(outputFile.exists())
    }

    @Test
    fun renderSearchPageProvidersDialog() {
        val bitmap = renderToBitmap(width = 412, height = 500) {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x40000000))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SearchPageProvidersDialog(
                        engines = sampleEngines,
                        hiddenEngineIds = setOf("yahoo", "ecosia"),
                        isDark = false,
                        onToggleEngine = { _, _ -> },
                        onDismiss = {}
                    )
                }
            }
        }

        val outputFile = File("build/search_page_providers_dialog_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(outputFile.exists())
    }
}
