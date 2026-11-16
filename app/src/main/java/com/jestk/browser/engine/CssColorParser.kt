package com.jestk.browser.engine

import android.util.Log

/**
 * Parses a CSS color as returned by `evaluateJavascript` (hex or `rgb[a]()`, possibly quoted)
 * into an opaque Android color; null for empty, transparent or nearly transparent values.
 */
internal fun parseCssColor(raw: String?): Int? {
    if (raw.isNullOrBlank()) return null
    val clean = raw.trim().removeSurrounding("\"").trim()
    if (clean.isEmpty() || clean == "null" || clean.equals("transparent", ignoreCase = true) ||
        clean == "rgba(0, 0, 0, 0)" || clean == "rgba(0,0,0,0)"
    ) {
        return null
    }
    if (clean.startsWith("#")) {
        return try {
            val hex = clean.substring(1)
            when (hex.length) {
                3 -> {
                    val r = hex[0].toString().repeat(2).toInt(16)
                    val g = hex[1].toString().repeat(2).toInt(16)
                    val b = hex[2].toString().repeat(2).toInt(16)
                    android.graphics.Color.rgb(r, g, b)
                }

                4 -> {
                    val r = hex[0].toString().repeat(2).toInt(16)
                    val g = hex[1].toString().repeat(2).toInt(16)
                    val b = hex[2].toString().repeat(2).toInt(16)
                    val a = hex[3].toString().repeat(2).toInt(16)
                    if (a < 25) null else android.graphics.Color.rgb(r, g, b)
                }

                6 -> {
                    android.graphics.Color.parseColor(clean)
                }

                8 -> {
                    // CSS hex #RRGGBBAA -> Android Color expects #AARRGGBB
                    val rr = clean.substring(1, 3).toInt(16)
                    val gg = clean.substring(3, 5).toInt(16)
                    val bb = clean.substring(5, 7).toInt(16)
                    val aa = clean.substring(7, 9).toInt(16)
                    if (aa < 25) null else android.graphics.Color.rgb(rr, gg, bb)
                }

                else -> {
                    null
                }
            }
        } catch (t: Throwable) {
            Log.w("HoriEngine", "Failed to parse CSS hex color: $clean", t)
            null
        }
    }
    if (clean.startsWith("rgb", ignoreCase = true)) {
        return try {
            val content = clean.substringAfter("(").substringBefore(")")
            val parts = content.split(",").map { it.trim() }
            if (parts.size >= 3) {
                val r = parts[0].toInt().coerceIn(0, 255)
                val g = parts[1].toInt().coerceIn(0, 255)
                val b = parts[2].toInt().coerceIn(0, 255)
                val a =
                    if (parts.size >= 4) {
                        val alphaFloat = parts[3].toFloatOrNull() ?: 1f
                        if (alphaFloat <= 1.0f) {
                            (alphaFloat * 255).toInt().coerceIn(0, 255)
                        } else {
                            alphaFloat.toInt().coerceIn(0, 255)
                        }
                    } else {
                        255
                    }
                if (a < 25) null else android.graphics.Color.rgb(r, g, b)
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.w("HoriEngine", "Failed to parse CSS rgb color: $clean", t)
            null
        }
    }
    return null
}
