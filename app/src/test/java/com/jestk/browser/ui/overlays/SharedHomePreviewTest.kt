package com.jestk.browser.ui.overlays

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.FavoriteItem
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.viewmodel.BrowserUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharedHomePreviewTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homeTabCards_drawOneSharedCaptureOfTheWorkspaceHome() {
        val tabs = (1..6).map { Tab(id = "t$it") } + Tab(id = "web", url = "https://example.com", title = "Example")
        val state = BrowserUiState(
            tabs = tabs,
            activeTabId = "t1",
            favorites = (1..4).map { FavoriteItem(id = it.toLong(), title = "Fav $it", url = "https://fav$it.com") },
        )
        lateinit var previews: SharedHomePreviews
        rule.setContent {
            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                previews = remember { SharedHomePreviews() }
                TabOverviewGrid(uiState = state, onEvent = {}, homePreviews = previews)
            }
        }
        val root = rule.activity.window.decorView
        fun drawFrame(): Bitmap {
            lateinit var bitmap: Bitmap
            rule.runOnUiThread {
                bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(android.graphics.Canvas(bitmap))
            }
            return bitmap
        }

        rule.waitForIdle()
        drawFrame() // records the hidden Home render
        rule.mainClock.advanceTimeBy(500) // captures it
        rule.waitForIdle()
        val bitmap = drawFrame() // cards draw the capture

        val workspaceId = tabs.first().workspaceId
        assertThat(previews.image(workspaceId)).isNotNull()
        File("build/tab_overview_grid_home_tabs_preview.png").apply { parentFile?.mkdirs() }
            .let { FileOutputStream(it).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) } }
    }

    @Test
    fun previewSize_matchesTheGridCardPreviewArea() {
        // 412 px grid: (412 - 32 - 20) / 3 = 120 wide, 120 * 16 / 9 - 38 = 175.3 tall.
        val size = homePreviewSize(412, Density(1f))
        assertThat(size.width.value).isWithin(0.01f).of(120f)
        assertThat(size.height.value).isWithin(0.1f).of(175.3f)
    }
}
