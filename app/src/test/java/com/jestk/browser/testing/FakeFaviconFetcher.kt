package com.jestk.browser.testing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.map.Mapper
import coil.request.Options
import com.jestk.browser.engine.SiteIconModel

/**
 * Serves every http(s) image request of a preview test from a generated bitmap, so previews never
 * touch the network (PRO-162). The picture depends only on the domain (the `domain` query value of
 * `https://www.google.com/s2/favicons?...`, else the host): a coloured square with the domain's first
 * letter, so two runs draw identical pixels.
 */
class FakeFaviconFetcher(
    private val data: Uri,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val domain = domainOf(data)
        return DrawableResult(
            drawable = BitmapDrawable(options.context.resources, render(domain)),
            isSampled = false,
            dataSource = DataSource.NETWORK,
        )
    }

    /**
     * Turns the model of a site's own icon into the origin URL, which [Factory] then serves from a
     * generated bitmap: the real fetcher would reach the network (PRO-184).
     */
    class SiteIconMapper : Mapper<SiteIconModel, Uri> {
        override fun map(data: SiteIconModel, options: Options): Uri = Uri.parse(data.origin)
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val scheme = data.scheme
            return if (scheme == "http" || scheme == "https") FakeFaviconFetcher(data, options) else null
        }
    }

    companion object {
        private val palette =
            intArrayOf(
                0xFF1E88E5.toInt(),
                0xFFE53935.toInt(),
                0xFF43A047.toInt(),
                0xFFFB8C00.toInt(),
                0xFF8E24AA.toInt(),
                0xFF00897B.toInt(),
            )

        internal fun domainOf(uri: Uri): String {
            val fromQuery = uri.getQueryParameter("domain")
            return (fromQuery ?: uri.host ?: "").removePrefix("www.")
        }

        internal fun colorFor(domain: String): Int = palette[Math.floorMod(domain.hashCode(), palette.size)]

        private fun render(domain: String): Bitmap {
            val size = 64
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(colorFor(domain))
            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = size * 0.6f
                    textAlign = Paint.Align.CENTER
                }
            val letter = domain.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            val baseline = size / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(letter, size / 2f, baseline, paint)
            return bitmap
        }
    }
}
