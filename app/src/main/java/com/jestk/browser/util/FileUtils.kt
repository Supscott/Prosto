package com.jestk.browser.util

import java.io.File

/**
 * Atomically moves this file to [targetFile]. If atomic rename fails (e.g. across filesystems
 * or on unusual storage mounts), falls back to copying and deleting the source file.
 * Returns true if the target file was successfully created and populated.
 */
fun File.atomicMoveTo(targetFile: File): Boolean {
    if (renameTo(targetFile)) return true
    return try {
        copyTo(targetFile, overwrite = true)
        delete()
        true
    } catch (_: Throwable) {
        false
    }
}
