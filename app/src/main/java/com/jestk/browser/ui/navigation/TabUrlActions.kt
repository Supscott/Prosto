package com.jestk.browser.ui.navigation

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.jestk.browser.R
import com.jestk.browser.util.ClipboardHelper

internal fun copyUrlToClipboard(
    context: Context,
    url: String,
) {
    if (url.isNotBlank() && !url.startsWith("about:")) {
        ClipboardHelper.copyToClipboard(context, url)
    } else {
        Toast.makeText(context, R.string.toast_no_url_to_copy, Toast.LENGTH_SHORT).show()
    }
}

internal fun shareWebpageUrl(
    context: Context,
    urlToShare: String,
) {
    if (urlToShare.isNotBlank() && !urlToShare.startsWith("about:")) {
        try {
            val sendIntent =
                Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, urlToShare)
                    type = "text/plain"
                }
            val shareIntent = Intent.createChooser(sendIntent, context.getString(R.string.share_link))
            context.startActivity(shareIntent)
        } catch (_: Exception) {
            Toast.makeText(context, R.string.toast_share_failed, Toast.LENGTH_SHORT).show()
        }
    } else {
        Toast.makeText(context, R.string.toast_no_url_to_share, Toast.LENGTH_SHORT).show()
    }
}
