package com.jestk.browser.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.jestk.browser.domain.model.ExternalAppPrompt

private const val TAG = "ExternalAppLauncher"

/**
 * Hands [prompt] over to the native app it was resolved for.
 *
 * Tries the stored intent first, then the app's launcher intent, and finally the Play Store web
 * listing when the app is not installed. Every failure is logged and swallowed: a missing app
 * must never crash the browser.
 */
internal fun Context.launchExternalApp(prompt: ExternalAppPrompt) {
    try {
        val intent = Intent.parseUri(prompt.intentUri, Intent.URI_INTENT_SCHEME).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        return
    } catch (e: Exception) {
        Log.w(TAG, "Failed to start the stored intent", e)
    }

    val pkg = prompt.appPackage
    val launchIntent = if (!pkg.isNullOrBlank()) {
        packageManager.getLaunchIntentForPackage(pkg)?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    } else {
        null
    }

    when {
        launchIntent != null -> try {
            startActivity(launchIntent)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to launch package via launch intent", t)
        }

        !prompt.isInstalled && !pkg.isNullOrBlank() -> try {
            val webPlay = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$pkg"),
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            startActivity(webPlay)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to launch play store fallback", t)
        }

        else -> Log.w(TAG, "No way to open the external app for ${prompt.domain}")
    }
}
