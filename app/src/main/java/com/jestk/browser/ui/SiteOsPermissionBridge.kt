package com.jestk.browser.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.jestk.browser.domain.model.SiteResource
import com.jestk.browser.ui.viewmodel.SiteOsPermissions

/**
 * Asks Android for CAMERA / RECORD_AUDIO on behalf of a page's camera or microphone request.
 * Create it while the Activity is being constructed (a property initializer): the launcher must
 * be registered before the Activity starts.
 */
internal class SiteOsPermissionBridge(private val activity: ComponentActivity) : SiteOsPermissions {
    private var onResult: ((Set<SiteResource>) -> Unit)? = null

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val callback = onResult ?: return@registerForActivityResult
        onResult = null
        callback(SiteResource.entries.filter { results[permissionOf(it)] == true }.toSet())
    }

    override fun isHeld(resource: SiteResource): Boolean =
        ContextCompat.checkSelfPermission(activity, permissionOf(resource)) == PackageManager.PERMISSION_GRANTED

    override fun request(missing: Set<SiteResource>, onResult: (granted: Set<SiteResource>) -> Unit) {
        this.onResult = onResult
        launcher.launch(missing.map(::permissionOf).toTypedArray())
    }

    private fun permissionOf(resource: SiteResource): String = when (resource) {
        SiteResource.CAMERA -> Manifest.permission.CAMERA
        SiteResource.MICROPHONE -> Manifest.permission.RECORD_AUDIO
    }
}
