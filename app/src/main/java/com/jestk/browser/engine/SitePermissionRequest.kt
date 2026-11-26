package com.jestk.browser.engine

import android.webkit.PermissionRequest
import com.jestk.browser.domain.model.SiteResource

/**
 * The part of a WebView `PermissionRequest` the camera / microphone flow uses, so the decision
 * logic runs without a WebView. [origin] is the grant key (see [siteOriginKey]), empty when the
 * page has no usable origin; [resources] holds only camera and microphone.
 */
interface SitePermissionRequest {
    val origin: String
    val host: String
    val resources: Set<SiteResource>

    /** Allows exactly [allowed], a subset of [resources]. */
    fun grant(allowed: Set<SiteResource>)

    fun deny()
}

/**
 * `scheme://host[:port]` in lower case, the default port left out; null for an opaque origin
 * (no scheme or host: `null`, `file:`, `data:`).
 */
fun siteOriginKey(scheme: String?, host: String?, port: Int): String? {
    val s = scheme?.lowercase().orEmpty()
    val h = host?.lowercase().orEmpty()
    if (s.isEmpty() || h.isEmpty()) return null
    val defaultPort = when (s) {
        "https" -> 443
        "http" -> 80
        else -> -1
    }
    return if (port == -1 || port == defaultPort) "$s://$h" else "$s://$h:$port"
}

/** Adapts a WebView request; two adapters are equal when they wrap the same request object. */
class WebViewSitePermissionRequest(private val raw: PermissionRequest) : SitePermissionRequest {
    override val origin: String = siteOriginKey(raw.origin?.scheme, raw.origin?.host, raw.origin?.port ?: -1).orEmpty()
    override val host: String = raw.origin?.host.orEmpty()
    override val resources: Set<SiteResource> = raw.resources.orEmpty().mapNotNull(::toSiteResource).toSet()

    override fun grant(allowed: Set<SiteResource>) {
        raw.grant(allowed.map(::toWebResource).toTypedArray())
    }

    override fun deny() = raw.deny()

    override fun equals(other: Any?): Boolean = other is WebViewSitePermissionRequest && other.raw === raw

    override fun hashCode(): Int = System.identityHashCode(raw)

    private fun toSiteResource(resource: String): SiteResource? = when (resource) {
        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> SiteResource.CAMERA
        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> SiteResource.MICROPHONE
        else -> null
    }

    private fun toWebResource(resource: SiteResource): String = when (resource) {
        SiteResource.CAMERA -> PermissionRequest.RESOURCE_VIDEO_CAPTURE
        SiteResource.MICROPHONE -> PermissionRequest.RESOURCE_AUDIO_CAPTURE
    }
}
