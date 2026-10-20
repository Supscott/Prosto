package com.jestk.browser.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import com.jestk.browser.domain.model.ExternalAppPrompt

sealed interface ExternalAppResolution {
    data class Offer(
        val prompt: ExternalAppPrompt,
        val isCustomScheme: Boolean
    ) : ExternalAppResolution

    /**
     * The page declared `S.browser_fallback_url` for a URL we cannot hand off. The page itself
     * must be loaded instead of leaving the WebView on a failed `intent://` navigation.
     */
    data class BrowserFallback(val url: String) : ExternalAppResolution

    data object NoApp : ExternalAppResolution
    data object UnresolvableScheme : ExternalAppResolution
}

object ExternalAppResolver {

    private val SEARCH_ENGINE_HOSTS = setOf(
        "google.com", "google.ru", "google.co.uk", "google.de", "google.fr",
        "yandex.ru", "yandex.com", "ya.ru",
        "duckduckgo.com",
        "bing.com",
        "search.yahoo.com", "yahoo.com",
        "ecosia.org",
        "search.brave.com", "brave.com"
    )

    val DOMAIN_ALIASES: Map<String, String> = mapOf(
        "vk.ru" to "vk.com",
        "m.vk.com" to "vk.com",
        "vk.me" to "vk.com",
        "redd.it" to "reddit.com",
        "old.reddit.com" to "reddit.com",
        "wb.ru" to "wildberries.ru",
        "ok.ru" to "odnoklassniki.ru",
        "fb.com" to "facebook.com",
        "fb.me" to "facebook.com",
        "t.me" to "telegram.me",
        "telegram.org" to "telegram.me",
        "x.com" to "twitter.com",
        "youtu.be" to "youtube.com",
        "m.youtube.com" to "youtube.com"
    )

    data class KnownDomainApp(
        val domain: String,
        val packageNames: List<String>,
        val displayName: String
    )

    val KNOWN_DOMAIN_APPS: Map<String, KnownDomainApp> = listOf(
        KnownDomainApp("reddit.com", listOf("com.reddit.frontpage", "ml.docilealligator.infinityforreddit", "me.ccrama.redditslide"), "Reddit"),
        KnownDomainApp("vk.com", listOf("com.vkontakte.android", "com.vk.im", "com.vk.video", "com.perm.kate_new_6"), "ВКонтакте"),
        KnownDomainApp("youtube.com", listOf("com.google.android.youtube", "com.google.android.apps.youtube.music"), "YouTube"),
        KnownDomainApp("telegram.me", listOf("org.telegram.messenger", "org.thunderdog.challegram", "org.telegram.plus"), "Telegram"),
        KnownDomainApp("instagram.com", listOf("com.instagram.android"), "Instagram"),
        KnownDomainApp("twitter.com", listOf("com.twitter.android"), "X (Twitter)"),
        KnownDomainApp("wildberries.ru", listOf("com.wildberries.ru"), "Wildberries"),
        KnownDomainApp("ozon.ru", listOf("ru.ozon.app.android"), "Ozon"),
        KnownDomainApp("avito.ru", listOf("com.avito.android"), "Авито"),
        KnownDomainApp("kinopoisk.ru", listOf("ru.kinopoisk"), "Кинопоиск"),
        KnownDomainApp("tiktok.com", listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), "TikTok"),
        KnownDomainApp("discord.com", listOf("com.discord"), "Discord"),
        KnownDomainApp("twitch.tv", listOf("tv.twitch.android.app"), "Twitch"),
        KnownDomainApp("spotify.com", listOf("com.spotify.music"), "Spotify"),
        KnownDomainApp("pinterest.com", listOf("com.pinterest"), "Pinterest"),
        KnownDomainApp("github.com", listOf("com.github.android"), "GitHub"),
        KnownDomainApp("2gis.ru", listOf("ru.dublgis.dgismobile"), "2ГИС"),
        KnownDomainApp("facebook.com", listOf("com.facebook.katana"), "Facebook")
    ).associateBy { it.domain }

    fun areDomainAliases(d1: String?, d2: String?): Boolean {
        if (d1.isNullOrBlank() || d2.isNullOrBlank()) return false
        val clean1 = d1.removePrefix("www.").lowercase()
        val clean2 = d2.removePrefix("www.").lowercase()
        if (clean1 == clean2) return true
        val canonical1 = DOMAIN_ALIASES[clean1] ?: clean1
        val canonical2 = DOMAIN_ALIASES[clean2] ?: clean2
        return canonical1 == canonical2
    }

    private val INTERNAL_SCHEMES = setOf(
        "about", "data", "javascript", "file", "blob", "content"
    )

    @Volatile
    private var cachedBrowserPackages: Set<String>? = null

    fun getGenericBrowserPackages(packageManager: PackageManager, selfPackageName: String): Set<String> {
        cachedBrowserPackages?.let { return it }
        val probeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://generic-browser-probe.example.internal")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val matches = try {
            packageManager.queryIntentActivities(probeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (t: Throwable) {
            emptyList()
        }
        val packages = matches.map { it.activityInfo.packageName }.toSet() + selfPackageName
        cachedBrowserPackages = packages
        return packages
    }

    fun clearBrowserPackagesCache() {
        cachedBrowserPackages = null
    }

    fun extractDomain(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase().orEmpty()
            if (scheme.isNotBlank() && scheme != "http" && scheme != "https" && scheme != "intent") {
                return scheme
            }
            val host = uri.host?.removePrefix("www.")?.lowercase()
            if (!host.isNullOrBlank()) {
                host
            } else {
                scheme
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun isSearchEngineDomain(host: String): Boolean {
        if (host.isBlank()) return false
        val cleanHost = host.removePrefix("www.").lowercase()
        return SEARCH_ENGINE_HOSTS.any { cleanHost == it || cleanHost.endsWith(".$it") }
    }

    fun resolve(
        context: Context,
        url: String,
        tabId: String,
        packageManager: PackageManager = context.packageManager
    ): ExternalAppResolution {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) return ExternalAppResolution.NoApp

        val uri = try {
            Uri.parse(trimmedUrl)
        } catch (e: Exception) {
            return ExternalAppResolution.NoApp
        }

        val scheme = uri.scheme?.lowercase() ?: return ExternalAppResolution.NoApp
        if (scheme in INTERNAL_SCHEMES) return ExternalAppResolution.NoApp

        val selfPackageName = context.packageName
        val genericBrowserPackages = getGenericBrowserPackages(packageManager, selfPackageName)

        // 1. Intent Scheme
        if (scheme == "intent") {
            return resolveIntentScheme(
                url = trimmedUrl,
                tabId = tabId,
                packageManager = packageManager,
                selfPackageName = selfPackageName,
                genericBrowserPackages = genericBrowserPackages
            )
        }

        // 2. Google Play Market Scheme
        if (scheme == "market") {
            return resolveMarketScheme(
                url = trimmedUrl,
                uri = uri,
                tabId = tabId,
                packageManager = packageManager,
                selfPackageName = selfPackageName
            )
        }

        // 3. System actions: tel, mailto, sms
        if (scheme == "tel" || scheme == "mailto" || scheme == "sms") {
            return resolveSystemActionScheme(
                url = trimmedUrl,
                scheme = scheme,
                tabId = tabId,
                packageManager = packageManager,
                selfPackageName = selfPackageName
            )
        }

        // 4. Standard HTTP / HTTPS
        if (scheme == "http" || scheme == "https") {
            return resolveHttpScheme(
                url = trimmedUrl,
                uri = uri,
                tabId = tabId,
                packageManager = packageManager,
                genericBrowserPackages = genericBrowserPackages
            )
        }

        // 5. Custom App Schemes (tg://, whatsapp://, viber://, etc.)
        return resolveCustomAppScheme(
            url = trimmedUrl,
            scheme = scheme,
            tabId = tabId,
            packageManager = packageManager,
            selfPackageName = selfPackageName
        )
    }

    /**
     * Queries activities for [intent], optionally allowing filters without `CATEGORY_DEFAULT`.
     * Every `PackageManager` failure degrades to "no match" instead of crashing navigation.
     */
    private fun queryMatches(
        packageManager: PackageManager,
        intent: Intent,
        excludedPackages: Set<String>,
        matchDefaultOnly: Boolean = true
    ): List<android.content.pm.ResolveInfo> = try {
        val flags = if (matchDefaultOnly) PackageManager.MATCH_DEFAULT_ONLY else 0
        packageManager.queryIntentActivities(intent, flags)
            .filter { it.activityInfo.packageName !in excludedPackages }
    } catch (t: Throwable) {
        emptyList()
    }

    private fun resolveIntentScheme(
        url: String,
        tabId: String,
        packageManager: PackageManager,
        selfPackageName: String,
        genericBrowserPackages: Set<String>
    ): ExternalAppResolution {
        val parsedIntent = try {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } catch (e: Exception) {
            Log.w("ExternalAppResolver", "Failed to parse intent URI: $url", e)
            return ExternalAppResolution.UnresolvableScheme
        }

        // Capture the package before component/selector are cleared: an intent:// URL may name
        // only a component, and that component is the only way to identify the target app.
        val componentPackage = parsedIntent.component?.packageName
        val targetPackages = listOfNotNull(parsedIntent.`package`, componentPackage)
            .filter { it.isNotBlank() && it != selfPackageName && it !in genericBrowserPackages }

        parsedIntent.addCategory(Intent.CATEGORY_BROWSABLE)
        parsedIntent.component = null
        parsedIntent.selector = null

        val excludedPackages = genericBrowserPackages + selfPackageName
        val domain = extractDomain(url).ifBlank { targetPackages.firstOrNull().orEmpty() }

        // Try the strict filter first, then relax the categories/flags. Many apps declare their
        // deep-link filters without CATEGORY_BROWSABLE or CATEGORY_DEFAULT.
        var matches = queryMatches(packageManager, parsedIntent, excludedPackages)
        if (matches.isEmpty()) {
            val withoutBrowsable = Intent(parsedIntent).apply {
                removeCategory(Intent.CATEGORY_BROWSABLE)
            }
            matches = queryMatches(packageManager, withoutBrowsable, excludedPackages)
        }
        if (matches.isEmpty()) {
            matches = queryMatches(packageManager, parsedIntent, excludedPackages, matchDefaultOnly = false)
        }

        if (matches.isNotEmpty()) {
            val match = matches.first()
            val appName = match.loadLabel(packageManager).toString()
            val appIcon = match.loadIcon(packageManager)?.toBitmapSafe()

            return ExternalAppResolution.Offer(
                prompt = ExternalAppPrompt(
                    tabId = tabId,
                    appName = appName,
                    appPackage = match.activityInfo.packageName,
                    appIcon = appIcon,
                    intentUri = parsedIntent.toUri(Intent.URI_INTENT_SCHEME),
                    isInstalled = true,
                    domain = domain
                ),
                isCustomScheme = true
            )
        }

        // The target package is installed but did not match the filter: offer the app itself
        // instead of sending an installed user to the Play Store.
        for (targetPackage in targetPackages) {
            if (!isPackageInstalled(packageManager, targetPackage)) continue
            val appInfo = getApplicationInfoSafe(packageManager, targetPackage)
            val appName = appInfo?.loadLabel(packageManager)?.toString()?.ifBlank { null } ?: targetPackage
            val appIcon = appInfo?.loadIcon(packageManager)?.toBitmapSafe()
            val directIntent = Intent(Intent.ACTION_VIEW, parsedIntent.data).apply {
                setPackage(targetPackage)
            }
            val canHandleDirectly = queryMatches(
                packageManager,
                directIntent,
                excludedPackages,
                matchDefaultOnly = false
            ).isNotEmpty()
            val targetIntent = when {
                canHandleDirectly -> directIntent
                else -> packageManager.getLaunchIntentForPackage(targetPackage) ?: directIntent
            }

            return ExternalAppResolution.Offer(
                prompt = ExternalAppPrompt(
                    tabId = tabId,
                    appName = appName,
                    appPackage = targetPackage,
                    appIcon = appIcon,
                    intentUri = targetIntent.toUri(Intent.URI_INTENT_SCHEME),
                    isInstalled = true,
                    domain = domain.ifBlank { targetPackage }
                ),
                isCustomScheme = true
            )
        }

        // The site declared how to fall back (usually a store or download page): honour it before
        // inventing our own market:// offer.
        val fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url")
        if (!fallbackUrl.isNullOrBlank() &&
            (fallbackUrl.startsWith("http://") || fallbackUrl.startsWith("https://"))
        ) {
            return ExternalAppResolution.BrowserFallback(fallbackUrl)
        }

        // App not installed and no site fallback: offer the market listing when the package is known.
        val missingPackage = targetPackages.firstOrNull()
        if (missingPackage != null) {
            return ExternalAppResolution.Offer(
                prompt = ExternalAppPrompt(
                    tabId = tabId,
                    appName = "",
                    appPackage = missingPackage,
                    appIcon = null,
                    intentUri = "market://details?id=$missingPackage",
                    isInstalled = false,
                    domain = domain.ifBlank { missingPackage }
                ),
                isCustomScheme = true
            )
        }

        return ExternalAppResolution.UnresolvableScheme
    }

    private fun resolveMarketScheme(
        url: String,
        uri: Uri,
        tabId: String,
        packageManager: PackageManager,
        selfPackageName: String
    ): ExternalAppResolution {
        val marketIntent = Intent(Intent.ACTION_VIEW, uri)
        val matches = try {
            packageManager.queryIntentActivities(marketIntent, PackageManager.MATCH_DEFAULT_ONLY)
                .filter { it.activityInfo.packageName != selfPackageName }
        } catch (t: Throwable) {
            emptyList()
        }

        val targetPackage = uri.getQueryParameter("id")
        val match = matches.firstOrNull()
        val appName = match?.loadLabel(packageManager)?.toString() ?: "Google Play"
        val appIcon = match?.loadIcon(packageManager)?.toBitmapSafe()

        return ExternalAppResolution.Offer(
            prompt = ExternalAppPrompt(
                tabId = tabId,
                appName = appName,
                appPackage = targetPackage,
                appIcon = appIcon,
                intentUri = url,
                isInstalled = matches.isNotEmpty(),
                domain = "play.google.com"
            ),
            isCustomScheme = true
        )
    }

    private fun resolveSystemActionScheme(
        url: String,
        scheme: String,
        tabId: String,
        packageManager: PackageManager,
        selfPackageName: String
    ): ExternalAppResolution {
        val actionIntent = when (scheme) {
            "tel" -> Intent(Intent.ACTION_DIAL, Uri.parse(url))
            "mailto" -> Intent(Intent.ACTION_SENDTO, Uri.parse(url))
            "sms" -> Intent(Intent.ACTION_SENDTO, Uri.parse(url))
            else -> Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }

        val matches = try {
            packageManager.queryIntentActivities(actionIntent, PackageManager.MATCH_DEFAULT_ONLY)
                .filter { it.activityInfo.packageName != selfPackageName }
        } catch (t: Throwable) {
            emptyList()
        }

        if (matches.isNotEmpty()) {
            val match = matches.first()
            val appName = match.loadLabel(packageManager).toString()
            val appIcon = match.loadIcon(packageManager)?.toBitmapSafe()

            return ExternalAppResolution.Offer(
                prompt = ExternalAppPrompt(
                    tabId = tabId,
                    appName = appName,
                    appPackage = match.activityInfo.packageName,
                    appIcon = appIcon,
                    intentUri = url,
                    isInstalled = true,
                    domain = scheme
                ),
                isCustomScheme = true
            )
        }

        return ExternalAppResolution.UnresolvableScheme
    }

    private fun resolveHttpScheme(
        url: String,
        uri: Uri,
        tabId: String,
        packageManager: PackageManager,
        genericBrowserPackages: Set<String>
    ): ExternalAppResolution {
        val host = uri.host?.removePrefix("www.")?.lowercase().orEmpty()
        if (host.isBlank() || isSearchEngineDomain(host)) {
            return ExternalAppResolution.NoApp
        }
        val canonicalDomain = DOMAIN_ALIASES[host] ?: host

        val candidateHosts = buildList {
            uri.host?.lowercase()?.let { add(it) }
            if (!host.startsWith("www.")) add("www.$host")
            add(host)
            add("m.$host")
            if (canonicalDomain != host) {
                add(canonicalDomain)
                add("www.$canonicalDomain")
                add("m.$canonicalDomain")
            }
        }.filter { it.isNotBlank() }.distinct()

        fun queryActivities(targetUri: Uri): List<android.content.pm.ResolveInfo> {
            val browsableIntent = Intent(Intent.ACTION_VIEW, targetUri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            val browsableMatches = try {
                packageManager.queryIntentActivities(browsableIntent, PackageManager.MATCH_DEFAULT_ONLY)
                    .filter { it.activityInfo.packageName !in genericBrowserPackages }
            } catch (t: Throwable) {
                emptyList()
            }
            if (browsableMatches.isNotEmpty()) return browsableMatches

            val defaultIntent = Intent(Intent.ACTION_VIEW, targetUri)
            val defaultMatches = try {
                packageManager.queryIntentActivities(defaultIntent, PackageManager.MATCH_DEFAULT_ONLY)
                    .filter { it.activityInfo.packageName !in genericBrowserPackages }
            } catch (t: Throwable) {
                emptyList()
            }
            if (defaultMatches.isNotEmpty()) return defaultMatches

            return try {
                packageManager.queryIntentActivities(defaultIntent, 0)
                    .filter { it.activityInfo.packageName !in genericBrowserPackages }
            } catch (t: Throwable) {
                emptyList()
            }
        }

        var matches: List<android.content.pm.ResolveInfo> = emptyList()
        var effectiveUri = uri

        for (candHost in candidateHosts) {
            val candUri = uri.buildUpon().authority(candHost).build()
            val found = queryActivities(candUri)
            if (found.isNotEmpty()) {
                matches = found
                effectiveUri = candUri
                break
            }
        }

        if (matches.isNotEmpty()) {
            val chosen = matches.first()
            val appName = chosen.loadLabel(packageManager).toString()
            val appIcon = chosen.loadIcon(packageManager)?.toBitmapSafe()
            val targetIntent = Intent(Intent.ACTION_VIEW, effectiveUri).apply {
                setPackage(chosen.activityInfo.packageName)
            }

            return ExternalAppResolution.Offer(
                prompt = ExternalAppPrompt(
                    tabId = tabId,
                    appName = appName,
                    appPackage = chosen.activityInfo.packageName,
                    appIcon = appIcon,
                    intentUri = targetIntent.toUri(Intent.URI_INTENT_SCHEME),
                    isInstalled = true,
                    domain = host
                ),
                isCustomScheme = false
            )
        }

        // Check curated KNOWN_DOMAIN_APPS registry for installed native apps that may only register deep link paths
        val known = KNOWN_DOMAIN_APPS[canonicalDomain] ?: KNOWN_DOMAIN_APPS[host]
        if (known != null) {
            for (pkg in known.packageNames) {
                if (pkg in genericBrowserPackages) continue
                val isInstalled = isPackageInstalled(packageManager, pkg)
                if (isInstalled) {
                    val appInfo = getApplicationInfoSafe(packageManager, pkg)
                    val appName = appInfo?.loadLabel(packageManager)?.toString()?.ifBlank { null }
                        ?: known.displayName
                    val appIcon = appInfo?.loadIcon(packageManager)?.toBitmapSafe()

                    val directIntent = Intent(Intent.ACTION_VIEW, effectiveUri).apply {
                        setPackage(pkg)
                    }
                    val canHandleDirectly = try {
                        packageManager.queryIntentActivities(directIntent, 0).isNotEmpty()
                    } catch (t: Throwable) {
                        false
                    }

                    val targetIntentUri = if (canHandleDirectly) {
                        directIntent.toUri(Intent.URI_INTENT_SCHEME)
                    } else {
                        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                        launchIntent?.toUri(Intent.URI_INTENT_SCHEME) ?: directIntent.toUri(Intent.URI_INTENT_SCHEME)
                    }

                    return ExternalAppResolution.Offer(
                        prompt = ExternalAppPrompt(
                            tabId = tabId,
                            appName = appName,
                            appPackage = pkg,
                            appIcon = appIcon,
                            intentUri = targetIntentUri,
                            isInstalled = true,
                            domain = host
                        ),
                        isCustomScheme = false
                    )
                }
            }
        }

        return ExternalAppResolution.NoApp
    }

    fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: Exception) {
            pm.getLaunchIntentForPackage(packageName) != null
        }
    }

    fun getApplicationInfoSafe(pm: PackageManager, packageName: String): android.content.pm.ApplicationInfo? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveCustomAppScheme(
        url: String,
        scheme: String,
        tabId: String,
        packageManager: PackageManager,
        selfPackageName: String
    ): ExternalAppResolution {
        val customIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val excludedPackages = setOf(selfPackageName)
        var matches = queryMatches(packageManager, customIntent, excludedPackages)
        if (matches.isEmpty()) {
            // Some apps register their scheme without CATEGORY_DEFAULT; the OS can still start them.
            matches = queryMatches(packageManager, customIntent, excludedPackages, matchDefaultOnly = false)
        }

        if (matches.isNotEmpty()) {
            val match = matches.first()
            val appName = match.loadLabel(packageManager).toString()
            val appIcon = match.loadIcon(packageManager)?.toBitmapSafe()

            return ExternalAppResolution.Offer(
                prompt = ExternalAppPrompt(
                    tabId = tabId,
                    appName = appName,
                    appPackage = match.activityInfo.packageName,
                    appIcon = appIcon,
                    intentUri = url,
                    isInstalled = true,
                    domain = scheme
                ),
                isCustomScheme = true
            )
        }

        return ExternalAppResolution.UnresolvableScheme
    }
}

fun Drawable.toBitmapSafe(targetWidth: Int = 96, targetHeight: Int = 96): Bitmap? {
    return try {
        if (this is BitmapDrawable && bitmap != null) {
            return bitmap
        }
        val w = if (intrinsicWidth > 0) intrinsicWidth else targetWidth
        val h = if (intrinsicHeight > 0) intrinsicHeight else targetHeight
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bmp
    } catch (e: Throwable) {
        null
    }
}
