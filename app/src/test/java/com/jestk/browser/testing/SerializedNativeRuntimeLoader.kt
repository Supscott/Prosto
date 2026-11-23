package com.jestk.browser.testing

import org.robolectric.nativeruntime.DefaultNativeRuntimeLoader
import java.nio.file.FileSystems

/**
 * Lets only one Robolectric sandbox load the native runtime at a time (PRO-112).
 *
 * Every sandbox (one per SDK / graphics mode) loads the native runtime on its first native call
 * (SQLite, native graphics). Room opens its database on a background thread, so a test can end
 * while that load is still running, and the next test starts another sandbox that loads at the
 * same time. [DefaultNativeRuntimeLoader.maybeCopyFonts] opens a zip file system on the fonts jar,
 * which is JVM-global: the second load throws `FileSystemAlreadyExistsException` (or, in newer
 * Robolectric, `ClosedFileSystemException`). `loaded` is already true by then, so that sandbox
 * never loads the library and every later native call in it fails with `UnsatisfiedLinkError`.
 *
 * Each sandbox has its own class loader, so a lock inside a test class would not be shared. The
 * lock is the `FileSystems` class object: bootstrap-loaded, so the same object in every sandbox.
 *
 * Registered through `META-INF/services/org.robolectric.pluginapi.NativeRuntimeLoader`. It has no
 * `@Priority`, so it outranks `DefaultNativeRuntimeLoader` (`Integer.MIN_VALUE`).
 */
class SerializedNativeRuntimeLoader : DefaultNativeRuntimeLoader() {
    override fun ensureLoaded() {
        synchronized(GLOBAL_LOAD_LOCK) { super.ensureLoaded() }
    }

    private companion object {
        val GLOBAL_LOAD_LOCK: Any = FileSystems::class.java
    }
}
