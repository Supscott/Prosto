---
name: compose-ui-preview
description: Visual inspection and iteration workflow for Android Jetpack Compose UI/UX using PNG screenshot rendering and Robolectric Native Graphics. Use whenever modifying or debugging Jetpack Compose UI components to visually verify appearance, alignment, padding, and theme modes.
---

# Jetpack Compose UI Visual Verification via PNG

This skill guides AI agents on how to render, view, and visually verify Jetpack Compose components as PNG images in the headless terminal environment without needing Android Studio or an Android emulator.

## Why Visual Verification is Essential

When modifying UI components (padding, background heights, typography, glassmorphism, contrast, clipping), code logic and unit tests alone cannot catch visual discrepancies (such as background blur extending too far, misaligned icons, or clipped text).

By rendering composables to PNG images and inspecting them directly with the `view_file` tool, agents can:

1. "See" the UI component as a user would.
2. Compare "Before" vs "After" side by side.
3. Catch theme mismatches (Light vs Dark mode).
4. Verify precise geometric alignment (e.g. background height matching a floating dock).

---

## The 4-Step Visual Iteration Workflow

```
1. Add/Locate @Preview Composable
       ↓
2. Render to PNG via Robolectric Test
       ↓
3. View PNG using view_file
       ↓
4. Visually verify and iterate
```

---

## Step 1: Define @Preview Composables

Always provide `@Preview` composables for both **Light** and **Dark** themes with realistic mock states:

```kotlin
@Preview(name = "Component Light", showBackground = true, backgroundColor = 0xFFFFF8F8)
@Composable
fun MyComponentLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            MyComponent(
                uiState = SampleData.mockState,
                onEvent = {}
            )
        }
    }
}

@Preview(name = "Component Dark", showBackground = true, backgroundColor = 0xFF161314)
@Composable
fun MyComponentDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161314)),
            contentAlignment = Alignment.Center
        ) {
            MyComponent(
                uiState = SampleData.mockState,
                onEvent = {}
            )
        }
    }
}
```

---

## Step 2: Render Composable to PNG with Robolectric

Use a lightweight Robolectric unit test with **Native Graphics Mode** (`@GraphicsMode(GraphicsMode.Mode.NATIVE)`). Native Graphics renders full typography, icons, gradients, and shapes onto an Android `Bitmap`.

### Reusable Test Template

Save this in `app/src/test/java/.../YourComponentPreviewTest.kt`:

```kotlin
package com.jestk.browser.ui.preview

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
class ComponentPreviewTest {

    @Test
    fun renderComponentPreview() {
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
                    Text("Light Theme", fontSize = 13.sp)
                    MyComponentLightPreview()

                    Text("Dark Theme", fontSize = 13.sp)
                    MyComponentDarkPreview()
                }
            }
        }

        activity.setContentView(composeView)

        // Specify canvas resolution (e.g. mobile width 440px)
        val width = 440
        val height = 956
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(bitmap))

        // Save to build/ (automatically gitignored)
        val outputFile = File("build/component_preview.png")
        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        println("Preview rendered to: ${outputFile.absolutePath}")
    }
}
```

### Run the Command

Execute the test using `rtk`:

```bash
rtk ./gradlew testDebugUnitTest --tests "com.jestk.browser.ui.preview.ComponentPreviewTest"
```

---

## Step 3: View the Screenshot with `view_file`

The agent environment natively supports image files. Call `view_file` on the generated PNG path:

```json
{
  "AbsolutePath": "/Users/ilya/CODING/browser/app/build/component_preview.png",
  "toolAction": "Inspecting UI preview",
  "toolSummary": "Inspecting UI preview"
}
```

The system will display the image directly in the model's context!

---

## Step 4: Visual Checklist for UI Verification

When inspecting the screenshot, examine:

1. **Vertical / Horizontal Alignment**: Are elements centered properly? Does a background container align with the exact height of child items (e.g. 58dp dock = 58dp background)?
2. **Padding & Insets**: Are there accidental "halos" or oversized gradient fades spilling over surrounding content?
3. **Contrast & Theme**: Is text easily readable against both light and dark backgrounds?
4. **Text Truncation**: Does title/domain ellipsize cleanly without vertical clipping?
5. **Icon & Pill Geometry**: Are corner radiuses smooth and borders (hairlines) crisp without artifacts?

---

## Side-by-Side Comparison Trick

When proposing or evaluating design changes, render a single image showing:

- Top: **Current / Before**
- Middle: **Option A**
- Bottom: **Option B**

This makes design decisions immediate, unambiguous, and easy to review with the user.
