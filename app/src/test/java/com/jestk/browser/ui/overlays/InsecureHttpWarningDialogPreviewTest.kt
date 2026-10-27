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
class InsecureHttpWarningDialogPreviewTest {

    @Test
    fun renderInsecureHttpWarningDialogPreview() {
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
                    InsecureHttpWarningDialogLightPreview()

                    Text("Dark Theme", fontSize = 13.sp, color = Color.White)
                    InsecureHttpWarningDialogDarkPreview()
                }
            }
        }

        activity.setContentView(composeView)

        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.AT_MOST)
        )
        composeView.layout(0, 0, composeView.measuredWidth, composeView.measuredHeight)

        val bitmap = Bitmap.createBitmap(
            composeView.measuredWidth,
            composeView.measuredHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        composeView.draw(canvas)

        val outputDir = File("build/reports/previews")
        outputDir.mkdirs()
        val outputFile = File(outputDir, "InsecureHttpWarningDialogPreviewTest.png")
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        println("Saved preview screenshot to: ${outputFile.absolutePath}")
    }
}
