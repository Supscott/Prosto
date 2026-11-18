package com.jestk.browser.ui

import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps a fullscreen video in the picture-in-picture window (PRO-55).
 *
 * When the Activity shrinks into PiP, Chromium WebView leaves HTML5 fullscreen on its own
 * (`exitFullscreenModeForTab` right after the window resize), so the PiP window would show the
 * whole page and players such as YouTube pause on the fullscreen exit. Once that exit is over,
 * the video element is pinned over the viewport with an inline style (its ancestors that would
 * trap a fixed element, such as a z-index stacking context or a transform, are neutralised the
 * same way) and resumed if it was playing. The original inline styles are kept here, not in the
 * page, and put back when PiP ends. The page sees no new globals and no replaced natives; only
 * inline styles change, and only while the PiP window is up.
 */
class PipVideoFill {
    private var savedStyles: List<String?>? = null

    /** Pins the main video of [webView] over its viewport; [resume] plays it if it is paused. */
    fun apply(webView: WebView, resume: Boolean, onVideoSize: (width: Int, height: Int) -> Unit) {
        webView.evaluateJavascript(applyScript(resume)) { result ->
            val parsed = parseResult(result) ?: return@evaluateJavascript
            savedStyles = parsed.styles
            if (parsed.width > 0 && parsed.height > 0) onVideoSize(parsed.width, parsed.height)
        }
    }

    /** Restores the original inline styles of the elements [apply] changed. */
    fun restore(webView: WebView?) {
        val styles = savedStyles ?: return
        savedStyles = null
        webView?.evaluateJavascript(restoreScript(styles), null)
    }

    internal data class Result(val styles: List<String?>, val width: Int, val height: Int)

    internal companion object {
        /** Marks the changed elements; the restore script finds them by this inline value. */
        const val MARKER_Z_INDEX = "2147483646"

        private const val ANCESTOR_STYLE =
            "z-index:$MARKER_Z_INDEX!important;transform:none!important;filter:none!important;" +
                "contain:none!important;will-change:auto!important"

        private const val FILL_STYLE =
            "position:fixed!important;inset:0!important;width:100vw!important;height:100vh!important;" +
                "max-width:none!important;max-height:none!important;object-fit:contain!important;" +
                "background:#000!important;transform:none!important;z-index:$MARKER_Z_INDEX!important"

        /**
         * Picks the fullscreen element's video, else the largest playing video, else the largest
         * one; returns `[originalStyles, videoWidth, videoHeight]` with the styles in document order.
         */
        fun applyScript(resume: Boolean): String =
            """
            (function(){
              var fs = document.fullscreenElement || document.webkitFullscreenElement;
              var list = Array.prototype.slice.call(document.querySelectorAll('video'));
              var area = function(v){ var r = v.getBoundingClientRect(); return r.width * r.height; };
              var pick = null;
              if (fs) pick = fs.tagName === 'VIDEO' ? fs : fs.querySelector('video');
              if (!pick) {
                var playing = list.filter(function(v){ return !v.paused && !v.ended; });
                var pool = playing.length ? playing : list;
                pool.forEach(function(v){ if (!pick || area(v) > area(pick)) pick = v; });
              }
              if (!pick) return null;
              var traps = [];
              for (var a = pick.parentElement; a && a !== document.documentElement; a = a.parentElement) {
                var cs = getComputedStyle(a);
                if (cs.zIndex !== 'auto' || cs.transform !== 'none' || cs.filter !== 'none' ||
                    cs.contain !== 'none' || cs.willChange !== 'auto') traps.push(a);
              }
              traps.reverse();
              var changed = traps.concat([pick]);
              var originals = changed.map(function(e){ return e.getAttribute('style'); });
              changed.forEach(function(e){
                e.setAttribute('style', (e.getAttribute('style') || '') + ';' +
                  (e === pick ? '$FILL_STYLE' : '$ANCESTOR_STYLE'));
              });
              if (${if (resume) "true" else "false"} && pick.paused) {
                var p = pick.play(); if (p && p.catch) p.catch(function(){});
              }
              return [originals, pick.videoWidth || 0, pick.videoHeight || 0];
            })();
            """.trimIndent()

        fun restoreScript(styles: List<String?>): String {
            val json = JSONArray(styles.map { it ?: JSONObject.NULL }).toString()
            return """
            (function(){
              var styles = $json;
              var marked = document.querySelectorAll('[style*="$MARKER_Z_INDEX"]');
              for (var i = 0; i < marked.length && i < styles.length; i++) {
                if (styles[i] === null) marked[i].removeAttribute('style');
                else marked[i].setAttribute('style', styles[i]);
              }
            })();
            """.trimIndent()
        }

        fun parseResult(raw: String?): Result? {
            if (raw.isNullOrBlank() || raw == "null") return null
            return try {
                val array = JSONArray(raw)
                val list = array.getJSONArray(0)
                val styles = (0 until list.length()).map { if (list.isNull(it)) null else list.getString(it) }
                Result(styles, array.optInt(1, 0), array.optInt(2, 0))
            } catch (_: Exception) {
                null
            }
        }
    }
}
