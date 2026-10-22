package com.jestk.browser.ui

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient

/**
 * Contract for hosts that are able to present the system file picker.
 *
 * The web engine needs to forward `WebChromeClient.onShowFileChooser` to whichever
 * Activity is currently hosting the WebView. Depending on the concrete Activity type
 * couples the rendering layer to a single implementation and breaks previews and
 * alternative hosts, so consumers resolve the Activity and then narrow it to this
 * interface instead.
 */
interface FileChooserHandler {
    /**
     * Opens the system file picker.
     *
     * @param callback Receives the selected URIs, or `null` when the pick was cancelled.
     * @param params Chrome's file chooser parameters, or `null` to use a generic picker.
     * @return `true` when the picker was launched, `false` otherwise.
     */
    fun openFileChooser(
        callback: ValueCallback<Array<Uri>>?,
        params: WebChromeClient.FileChooserParams?,
    ): Boolean
}
