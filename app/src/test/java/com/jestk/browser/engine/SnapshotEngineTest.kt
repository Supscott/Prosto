package com.jestk.browser.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.util.atomicMoveTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SnapshotEngineTest {

    @Test
    fun deleteTabSnapshot_removesSpecificTabSnapshot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SnapshotEngine.init(context)

        val snapshotsDir = File(context.filesDir, "snapshots").apply { mkdirs() }
        val snapshot1 = File(snapshotsDir, "tab_tab123.jpg").apply { writeBytes("img1".toByteArray()) }
        val snapshot2 = File(snapshotsDir, "tab_tab456.jpg").apply { writeBytes("img2".toByteArray()) }

        assertThat(snapshot1.exists()).isTrue()
        assertThat(snapshot2.exists()).isTrue()

        SnapshotEngine.deleteTabSnapshot("tab123")

        assertThat(snapshot1.exists()).isFalse()
        assertThat(snapshot2.exists()).isTrue()
    }

    @Test
    fun deleteTabSnapshot_withExplicitContext_removesSpecificTabSnapshot() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val snapshotsDir = File(context.filesDir, "snapshots").apply { mkdirs() }
        val snapshot = File(snapshotsDir, "tab_tab789.jpg").apply { writeBytes("img".toByteArray()) }

        assertThat(snapshot.exists()).isTrue()
        SnapshotEngine.deleteTabSnapshot(context, "tab789")
        assertThat(snapshot.exists()).isFalse()
    }

    @Test
    fun deleteAllSnapshots_removesAllSnapshotsInDirectory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SnapshotEngine.init(context)

        val snapshotsDir = File(context.filesDir, "snapshots").apply { mkdirs() }
        val snapshot1 = File(snapshotsDir, "tab_1.jpg").apply { writeBytes("img1".toByteArray()) }
        val snapshot2 = File(snapshotsDir, "tab_2.jpg").apply { writeBytes("img2".toByteArray()) }

        assertThat(snapshot1.exists()).isTrue()
        assertThat(snapshot2.exists()).isTrue()

        SnapshotEngine.deleteAllSnapshots()

        assertThat(snapshot1.exists()).isFalse()
        assertThat(snapshot2.exists()).isFalse()
    }

    @Test
    fun shutdown_cancelsTheBackgroundScope() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SnapshotEngine.init(context)

        // Make sure the engine owns a running scope, whatever a previous teardown did.
        assertThat(SnapshotEngine.ensureScope()).isTrue()
        assertThat(SnapshotEngine.isScopeActive()).isTrue()

        SnapshotEngine.shutdown()
        assertThat(SnapshotEngine.isScopeActive()).isFalse()

        // The call is idempotent.
        SnapshotEngine.shutdown()
        assertThat(SnapshotEngine.isScopeActive()).isFalse()
    }

    @Test
    fun ensureScope_recreatesTheScopeAfterShutdown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SnapshotEngine.init(context)

        SnapshotEngine.shutdown()
        assertThat(SnapshotEngine.isScopeActive()).isFalse()

        // The engine is an app scoped singleton: it must stay usable after a shutdown.
        assertThat(SnapshotEngine.ensureScope()).isTrue()
        assertThat(SnapshotEngine.isScopeActive()).isTrue()
    }

    @Test
    fun init_doesNotCancelTheRunningScope() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        SnapshotEngine.init(context)
        assertThat(SnapshotEngine.ensureScope()).isTrue()

        // The engine is initialised on every capture: re-initialising must not cancel the scope.
        SnapshotEngine.init(context)
        SnapshotEngine.init(context.applicationContext)

        assertThat(SnapshotEngine.isScopeActive()).isTrue()
    }

    @Test
    fun atomicStaging_createsFinalSnapshotWithoutLeavingTempFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SnapshotEngine.init(context)

        val snapshotsDir = File(context.filesDir, "snapshots").apply { mkdirs() }
        val tempFile = File(snapshotsDir, "tab_test123.jpg.tmp").apply { writeBytes("bitmap_bytes".toByteArray()) }
        val targetFile = File(snapshotsDir, "tab_test123.jpg")

        val moved = tempFile.atomicMoveTo(targetFile)
        assertThat(moved).isTrue()
        assertThat(tempFile.exists()).isFalse()
        assertThat(targetFile.exists()).isTrue()
        assertThat(targetFile.readText()).isEqualTo("bitmap_bytes")
    }
}
