---
name: duckduckgo-reference
description: "Consult the duckduckgo/Android open-source repository as an architectural and behavioral baseline for WebView compatibility, privacy features, and Android web-engine edge cases."
---

# DuckDuckGo Android Reference Guide

This skill guides coding agents on how to leverage the official open-source **DuckDuckGo Android Browser** repository ([`duckduckgo/Android`](https://github.com/duckduckgo/Android)) to research, design, and implement robust solutions for **Prosto Browser**.

---

## Why DuckDuckGo Android is Our Primary Reference

Both **Prosto Browser** and **DuckDuckGo for Android** share the same foundational web engine: **Android Chromium `WebView`** (`android.webkit.WebView` + `androidx.webkit.*`).

Android WebView documentation often omits edge cases in Chromium's internal behaviors (e.g. Client Hints GREASE versioning, third-party cookie partitioning, Service Worker interaction, Turnstile/reCAPTCHA bot detection). DuckDuckGo has solved and field-tested hundreds of these edge cases across millions of production devices.

**Consult DuckDuckGo when dealing with:**
1. **Web Compatibility & Anti-Bot**: Turnstile, Cloudflare WAF, Google login, reCAPTCHA, Client Hints (`UserAgentMetadata`), User-Agent reduction.
2. **Network & Interception**: `shouldInterceptRequest`, `shouldOverrideUrlLoading`, HTTP header filtering, tracker blocking.
3. **Cookie Policies & Storage**: `CookieManager`, `setAcceptThirdPartyCookies`, partitioned cookies, site data clearing ("Fire" button).
4. **Content Scripts & Privacy**: Content scope scripts, autoconsent/cookie banners, Global Privacy Control (GPC), monkey-patch stealth.
5. **Downloads & Schemes**: Blob downloads, data URLs, Content-Disposition, external app intents (`intent://`, `tg://`).
6. **Media & Fullscreen**: Custom video presentation, `WebChromeClient.onShowCustomView`, autoplay without gesture requirements.
7. **Crash Resilience**: `onRenderProcessGone`, renderer termination recovery.

---

## Repository Map (`duckduckgo/Android`)

DuckDuckGo is organized into Gradle feature modules. The key modules to inspect are:

| Domain / Feature | DuckDuckGo Module Path | Key Classes & Files |
|---|---|---|
| **User-Agent & Client Hints** | `user-agent/user-agent-impl/` | `UserAgentProvider.kt`, `ClientBrandHintProvider.kt`, `UserAgentInterceptor.kt` |
| **Core Web Navigation** | `app/src/main/java/com/duckduckgo/app/browser/` | `BrowserWebViewClient.kt`, `BrowserChromeClient.kt` |
| **Cookie & Storage Policy** | `app/src/main/java/com/duckduckgo/app/browser/cookies/` | `ThirdPartyCookieManager.kt`, `CookieManagerProvider.kt` |
| **App Schemes & External Links** | `app/src/main/java/com/duckduckgo/app/browser/applinks/` | `AppLinksLauncher.kt`, `AppSchemeInterceptionFeature.kt` |
| **Downloads & Media** | `downloads/`, `media-playback/` | `DownloadCallback.kt`, `BlobDownloader.kt` |
| **Auto-Consent & Banners** | `autoconsent/autoconsent-impl/` | `AutoconsentFeature.kt`, `RealAutoconsent.kt` |
| **Content Scope Scripts** | `contentscopescripts/` | `ContentScopeScripts.kt`, UserScript injection |
| **Certificate Trust** | `app/src/main/java/com/duckduckgo/app/browser/certificates/` | `TrustedCertificateStore.kt` |

*Note: The default branch on GitHub is `develop`.*

---

## How to Query & Inspect the Codebase

### Method 1: GitHub Raw Content (Fastest for Specific Files)
Fetch raw Kotlin sources directly via `curl`:
```bash
# Example: Fetch ClientBrandHintProvider implementation
curl -s "https://raw.githubusercontent.com/duckduckgo/Android/develop/user-agent/user-agent-impl/src/main/java/com/duckduckgo/user/agent/impl/ClientBrandHintProvider.kt"

# Example: Fetch UserAgentProvider implementation
curl -s "https://raw.githubusercontent.com/duckduckgo/Android/develop/user-agent/user-agent-impl/src/main/java/com/duckduckgo/user/agent/impl/UserAgentProvider.kt"

# Example: Inspect BrowserWebViewClient
curl -s "https://raw.githubusercontent.com/duckduckgo/Android/develop/app/src/main/java/com/duckduckgo/app/browser/BrowserWebViewClient.kt"
```

### Method 2: GitHub API (Directory Browsing)
List files in a module when unsure of exact file names:
```bash
curl -s "https://api.github.com/repos/duckduckgo/Android/contents/user-agent/user-agent-impl/src/main/java/com/duckduckgo/user/agent/impl" | jq -r '.[].name'
```

### Method 3: Web Search for Specific Concepts
Use `search_web` with the `site:github.com/duckduckgo/Android` filter:
```
site:github.com/duckduckgo/Android "setAcceptThirdPartyCookies"
site:github.com/duckduckgo/Android "WebSettingsCompat.getUserAgentMetadata"
```

### Method 4: Ephemeral Shallow Clone (For Broad Research)
If researching a large subsystem spanning multiple modules:
```bash
git clone --depth 1 https://github.com/duckduckgo/Android.git /tmp/duckduckgo-android
# Grep across the codebase locally
grep -rn "onRenderProcessGone" /tmp/duckduckgo-android/
```

---

## Adaptation Checklist for Prosto Browser

When borrowing or porting an idea from DuckDuckGo, **never copy-paste blindly**. Adapt it to Prosto's standards:

1. **Dependency Injection**:
   - DuckDuckGo uses Dagger/Anvil/Metro (`@ContributesBinding`, `@Inject`, `@SingleIn`).
   - Prosto uses lightweight direct singletons, Kotlin objects, or constructor injection inside ViewModel state holders. Strip DI framework annotations.
2. **Domain Layer Purity**:
   - Never import Android framework UI types (`android.view.*`, `android.graphics.Bitmap`) into `com.jestk.browser.domain`. Wrap binary data in domain value classes (`ByteArray` with content-equality).
3. **No Blur Invariant**:
   - DuckDuckGo might use dialog blurs or custom surface renderers. Prosto strictly prohibits `Modifier.blur()` and `dev.chrisbanes.haze` to prevent WebView hardware frame flicker on Android 12+.
4. **Favicon Subsystem**:
   - Keep `WebChromeClient.onReceivedIcon` as the sole source of truth. Never adapt favicon extraction from `WebViewClient`.
5. **Logging**:
   - DuckDuckGo uses `logcat { ... }` from Square Logcat. Prosto uses Android's standard `android.util.Log` (`Log.d(TAG, ...)`).
6. **Localization**:
   - Ensure all user-facing strings are added to both `res/values/strings.xml` and `res/values-ru/strings.xml`.

---

## Case Study: Cloudflare Turnstile & Client Hints Fix

### Problem
`kagane.moe` failed Cloudflare Turnstile verification with an infinite checkbox loop. After initial bypass, subresource cover images failed with `HTTP 403 Forbidden` (`cf-mitigated: challenge`).

### DuckDuckGo Discovery
We queried DuckDuckGo's `RealClientBrandHintProvider.kt`:
```kotlin
val metadata = WebSettingsCompat.getUserAgentMetadata(settings)
val finalBrandList = metadata.brandVersionList.map {
    if (it.brand.contains("Android WebView")) {
        BrandVersion.Builder()
            .setBrand("Google Chrome")
            .setFullVersion(it.fullVersion)
            .setMajorVersion(it.majorVersion)
            .build()
    } else {
        it
    }
}
```

### Key Lesson
DuckDuckGo **does not synthesize GREASE brands from scratch**. It queries the native `metadata = WebSettingsCompat.getUserAgentMetadata(settings)` initialized by Chromium, keeps Chromium's genuine GREASE brand (`Not?A_Brand`), preserves entropy fields (`bitness`, `architecture`, `platformVersion`, `model`), and only changes `"Android WebView"` $\to$ `"Google Chrome"`.

Adopting this exact pattern in Prosto's [`UserAgentMetadataProvider.kt`](file:///Users/ilya/CODING/browser/app/src/main/java/com/jestk/browser/engine/UserAgentMetadataProvider.kt) resolved all Cloudflare challenges and 403 subresource failures immediately.
