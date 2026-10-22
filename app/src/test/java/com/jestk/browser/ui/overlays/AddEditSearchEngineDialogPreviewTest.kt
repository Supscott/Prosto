package com.jestk.browser.ui.overlays

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.util.VibrationFeedback
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
class AddEditSearchEngineDialogPreviewTest {

    @Test
    fun renderAddEditSearchEngineDialogPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val composeView = ComposeView(activity).apply {
            setContent {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF9EACB8))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text("Light Theme", fontSize = 13.sp, color = Color.Black)
                    AddEditSearchEngineDialogLightPreview()

                    Text("Dark Theme", fontSize = 13.sp, color = Color.White)
                    AddEditSearchEngineDialogDarkPreview()
                }
            }
        }

        activity.setContentView(composeView)

        val width = 440
        val height = 1600
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/add_edit_search_engine_dialog_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(outputFile.exists())
    }

    @Test
    fun renderSearchEngineDropdownCardPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val composeView = ComposeView(activity).apply {
            setContent {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF9EACB8))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text("Normal Mode", fontSize = 13.sp, color = Color.Black)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                                SearchEngineDropdownCard(
                                    selectedEngine = SearchEngine.GOOGLE,
                                    onSelectEngine = {},
                                    onAddEngineClick = {},
                                    onEditEngineClick = {},
                                    onDeleteEngineClick = {},
                                    onReorderEngines = {},
                                    isDark = false,
                                    engines = SearchEngine.DEFAULT_ENGINES,
                                    initialEditMode = false,
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                                SearchEngineDropdownCard(
                                    selectedEngine = SearchEngine.DUCKDUCKGO,
                                    onSelectEngine = {},
                                    onAddEngineClick = {},
                                    onEditEngineClick = {},
                                    onDeleteEngineClick = {},
                                    onReorderEngines = {},
                                    isDark = true,
                                    engines = SearchEngine.DEFAULT_ENGINES,
                                    initialEditMode = false,
                                )
                            }
                        }
                    }

                    Text("Edit Mode", fontSize = 13.sp, color = Color.Black)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                                SearchEngineDropdownCard(
                                    selectedEngine = SearchEngine.GOOGLE,
                                    onSelectEngine = {},
                                    onAddEngineClick = {},
                                    onEditEngineClick = {},
                                    onDeleteEngineClick = {},
                                    onReorderEngines = {},
                                    isDark = false,
                                    engines = SearchEngine.DEFAULT_ENGINES,
                                    initialEditMode = true,
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                                SearchEngineDropdownCard(
                                    selectedEngine = SearchEngine.DUCKDUCKGO,
                                    onSelectEngine = {},
                                    onAddEngineClick = {},
                                    onEditEngineClick = {},
                                    onDeleteEngineClick = {},
                                    onReorderEngines = {},
                                    isDark = true,
                                    engines = SearchEngine.DEFAULT_ENGINES,
                                    initialEditMode = true,
                                )
                            }
                        }
                    }
                }
            }
        }

        activity.setContentView(composeView)
        Thread.sleep(200)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val width = 560
        val height = 800
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/search_engine_dropdown_card_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(outputFile.exists())
    }

    @Test
    fun renderSearchEngineDropdownCardDragPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val dummyVibration = object : VibrationFeedback {
            override fun tick() {}
            override fun confirm() {}
        }
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)

        val lightDragState = SearchEngineReorderState(
            slotHeightPx = 48f * activity.resources.displayMetrics.density,
            coroutineScope = scope,
            vibration = dummyVibration,
            onReorder = {}
        ).apply {
            onDragStart(0)
            onDrag(26f * activity.resources.displayMetrics.density, SearchEngine.DEFAULT_ENGINES.size)
        }

        val darkDragState = SearchEngineReorderState(
            slotHeightPx = 48f * activity.resources.displayMetrics.density,
            coroutineScope = scope,
            vibration = dummyVibration,
            onReorder = {}
        ).apply {
            onDragStart(0)
            onDrag(56f * activity.resources.displayMetrics.density, SearchEngine.DEFAULT_ENGINES.size)
        }

        val composeView = ComposeView(activity).apply {
            setContent {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE2E8F0))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Dropdown Card Reorder Drag (Light & Dark)", fontSize = 14.sp, color = Color.Black)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
                                SearchEngineDropdownCard(
                                    selectedEngine = SearchEngine.GOOGLE,
                                    onSelectEngine = {},
                                    onAddEngineClick = {},
                                    onEditEngineClick = {},
                                    onDeleteEngineClick = {},
                                    onReorderEngines = {},
                                    isDark = false,
                                    engines = SearchEngine.DEFAULT_ENGINES,
                                    initialEditMode = true,
                                    reorderState = lightDragState
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            JestkBrowserTheme(themeMode = ThemeMode.DARK) {
                                SearchEngineDropdownCard(
                                    selectedEngine = SearchEngine.DUCKDUCKGO,
                                    onSelectEngine = {},
                                    onAddEngineClick = {},
                                    onEditEngineClick = {},
                                    onDeleteEngineClick = {},
                                    onReorderEngines = {},
                                    isDark = true,
                                    engines = SearchEngine.DEFAULT_ENGINES,
                                    initialEditMode = true,
                                    reorderState = darkDragState
                                )
                            }
                        }
                    }
                }
            }
        }

        activity.setContentView(composeView)
        Thread.sleep(200)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val width = 560
        val height = 450
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        val outputFile = File("build/search_engine_dropdown_drag_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue(outputFile.exists())
    }
}
