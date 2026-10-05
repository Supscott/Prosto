package com.jestk.browser.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
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
}
