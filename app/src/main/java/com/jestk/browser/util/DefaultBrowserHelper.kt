package com.jestk.browser.util

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher

object DefaultBrowserHelper {

    fun isDefaultBrowser(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager != null) {
                    return roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
                }
            } catch (_: Exception) {}
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    fun createSetDefaultBrowserIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
            }
        }
        return Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    }

    /**
     * Opens the system UI to choose or configure the default browser.
     *
     * 1. If running on Android Q+ and an [ActivityResultLauncher] is provided, attempts to request
     *    the browser role via [RoleManager.createRequestRoleIntent] (which requires an activity result
     *    so that Android's RequestRoleActivity can verify the calling package).
     * 2. If RoleManager is not available, fails, or launcher is null, falls back to opening
     *    [Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS].
     * 3. If managing default apps settings is unavailable on this device/ROM, falls back to
     *    opening the app's details settings ([Settings.ACTION_APPLICATION_DETAILS_SETTINGS]).
     * 4. As a last resort, opens general system settings ([Settings.ACTION_SETTINGS]).
     */
    fun openDefaultBrowserSettings(
        context: Context,
        launcher: ActivityResultLauncher<Intent>? = null
    ) {
        var launched = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && launcher != null) {
            try {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager != null) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                    launcher.launch(intent)
                    launched = true
                }
            } catch (_: Exception) {
                launched = false
            }
        }

        if (!launched) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                if (launcher != null) {
                    launcher.launch(intent)
                } else {
                    context.startActivity(intent)
                }
                launched = true
            } catch (_: Exception) {
                launched = false
            }
        }

        if (!launched) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                if (launcher != null) {
                    launcher.launch(intent)
                } else {
                    context.startActivity(intent)
                }
                launched = true
            } catch (_: Exception) {
                launched = false
            }
        }

        if (!launched) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                if (launcher != null) {
                    launcher.launch(intent)
                } else {
                    context.startActivity(intent)
                }
            } catch (_: Exception) {}
        }
    }
}
