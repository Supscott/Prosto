package com.jestk.browser.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

object ClipboardHelper {
    fun copyToClipboard(
        context: Context,
        text: String,
        label: String = "URL",
        showToast: Boolean = true,
        toastMessage: String? = null
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        if (showToast) {
            Toast.makeText(context, toastMessage ?: "URL copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }
}
