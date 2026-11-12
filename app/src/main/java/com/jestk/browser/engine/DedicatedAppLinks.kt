package com.jestk.browser.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import com.jestk.browser.domain.model.ExternalAppPrompt

/**
 * Web links that an installed app claims by their exact path, such as Linear's
 * `https://linear.app/auth/redirect` sign-in handoff (PRO-15).
 *
 * Prosto loads every web link itself and only hints that an app exists. That is right for normal
 * browsing (a Reddit link while reading Reddit), but a link whose exact path an app declares in
 * its manifest exists only to hand over to that app: loading it in the browser does nothing
 * useful and can use up a one-time sign-in code. A deliberate tap on such a link asks to open the
 * app instead; Cancel loads the page as before.
 */
internal object DedicatedAppLinks {

    /**
     * The confirmation to show for a deliberate tap on [url], or `null` when no installed app
     * (other than a browser or Prosto) claims the exact path of [url].
     */
    fun resolve(context: Context, url: String, tabId: String): ExternalAppPrompt? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val pm = context.packageManager
        val excluded = ExternalAppResolver.getGenericBrowserPackages(pm, context.packageName)
        val match = findExactPathHandler(pm, uri, excluded) ?: return null
        val pkg = match.activityInfo.packageName
        return ExternalAppPrompt(
            tabId = tabId,
            appName = match.loadLabel(pm).toString(),
            appPackage = pkg,
            appIcon = match.loadIcon(pm)?.toAppIcon(),
            intentUri = Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).toUri(Intent.URI_INTENT_SCHEME),
            domain = uri.host?.removePrefix("www.")?.lowercase().orEmpty(),
            requiresConfirmation = true,
            webFallbackUrl = url,
        )
    }

    /** Shows the confirmation for [url] through [deliver]; `true` when the navigation must stop. */
    fun offer(
        context: Context,
        url: String,
        tabId: String,
        deliver: ((tabId: String, prompt: ExternalAppPrompt, userInitiated: Boolean) -> Unit)?,
    ): Boolean {
        if (deliver == null) return false
        val prompt = resolve(context, url, tabId) ?: return false
        deliver(tabId, prompt, true)
        return true
    }

    /** The first non-browser activity whose filter names the path of [uri] literally. */
    fun findExactPathHandler(pm: PackageManager, uri: Uri, excludedPackages: Set<String>): ResolveInfo? {
        val path = uri.path?.takeIf { it.length > 1 } ?: return null
        val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        val matches = try {
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_RESOLVED_FILTER)
        } catch (t: Throwable) {
            emptyList()
        }
        return matches.firstOrNull { info ->
            info.activityInfo.packageName !in excludedPackages && info.filter?.namesPathLiterally(path) == true
        }
    }

    private fun IntentFilter.namesPathLiterally(path: String): Boolean {
        val paths = pathsIterator() ?: return false
        for (pattern in paths) {
            if (pattern.type == android.os.PatternMatcher.PATTERN_LITERAL && pattern.path == path) return true
        }
        return false
    }
}
