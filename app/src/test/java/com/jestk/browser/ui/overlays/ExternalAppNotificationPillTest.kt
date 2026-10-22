package com.jestk.browser.ui.overlays

import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.domain.model.ExternalAppPrompt
import com.jestk.browser.ui.theme.JestkBrowserTheme
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
class ExternalAppNotificationPillTest {

    @Test
    fun renderExternalAppNotificationPillPreview() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        val samplePrompt = ExternalAppPrompt(
            id = "test_prompt_1",
            tabId = "tab_1",
            appName = "YouTube",
            appPackage = "com.google.android.youtube",
            intentUri = "https://youtube.com/watch?v=123",
            isInstalled = true,
            domain = "youtube.com"
        )

        val genericPrompt = ExternalAppPrompt(
            id = "test_prompt_2",
            tabId = "tab_1",
            appName = "",
            appPackage = "com.example.app",
            intentUri = "market://details?id=com.example.app",
            isInstalled = false,
            domain = "com.example.app"
        )

        val composeView = ComposeView(activity).apply {
            setContent {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE2E8F0))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Light Theme - Specific App", fontSize = 12.sp, color = Color.Black)
                    JestkBrowserTheme(themeMode = com.jestk.browser.domain.model.ThemeMode.LIGHT) {
                        ExternalAppNotificationPill(
                            prompt = samplePrompt,
                            onOpen = {},
                            onDismiss = {}
                        )
                    }

                    Text("Light Theme - Generic App", fontSize = 12.sp, color = Color.Black)
                    JestkBrowserTheme(themeMode = com.jestk.browser.domain.model.ThemeMode.LIGHT) {
                        ExternalAppNotificationPill(
                            prompt = genericPrompt,
                            onOpen = {},
                            onDismiss = {}
                        )
                    }

                    Text("Dark Theme - Specific App", fontSize = 12.sp, color = Color.Black)
                    JestkBrowserTheme(themeMode = com.jestk.browser.domain.model.ThemeMode.DARK) {
                        ExternalAppNotificationPill(
                            prompt = samplePrompt,
                            onOpen = {},
                            onDismiss = {}
                        )
                    }
                }
            }
        }

        activity.setContentView(composeView)

        val width = 420
        val height = 450
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        composeView.draw(canvas)

        val outputDir = File("build/outputs/roborazzi")
        if (!outputDir.exists()) outputDir.mkdirs()
        val outputFile = File(outputDir, "external_app_notification_pill.png")
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assert(outputFile.exists() && outputFile.length() > 0)
    }
}
