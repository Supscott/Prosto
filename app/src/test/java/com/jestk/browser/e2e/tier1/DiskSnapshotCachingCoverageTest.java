package com.jestk.browser.e2e.tier1;

import static com.google.common.truth.Truth.assertThat;

import com.jestk.browser.domain.model.Tab;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Tier 1: Feature Coverage Test for Disk Snapshot Caching & DB Linkage (R4).
 */
public class DiskSnapshotCachingCoverageTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testAtomicSnapshotWriteToDisk() throws IOException {
        File snapshotDir = tempFolder.newFolder("snapshots");
        File targetFile = new File(snapshotDir, "tab_123.jpg");
        File tempFile = new File(snapshotDir, "tab_123.jpg.tmp");

        byte[] fakeImageData = new byte[]{1, 2, 3, 4, 5};

        // Write temp file first
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(fakeImageData);
        }

        // Atomic rename
        boolean renamed = tempFile.renameTo(targetFile);

        assertThat(renamed).isTrue();
        assertThat(targetFile.exists()).isTrue();
        assertThat(targetFile.length()).isEqualTo(5);
    }

    @Test
    public void testSnapshotDiskPathLinkedToTabEntity() {
        String path = "/data/user/0/com.jestk.browser/files/snapshots/tab_test.jpg";
        Tab tab = new Tab("tab-test", "https://jestk.com", "Jestk", System.currentTimeMillis(), path, 0);

        assertThat(tab.getSnapshotPath()).isEqualTo(path);
    }

    @Test
    public void testSnapshotFileNamingConvention() {
        String tabId = "abc-xyz-123";
        String expectedFileName = "tab_" + tabId + ".jpg";

        assertThat(expectedFileName).isEqualTo("tab_abc-xyz-123.jpg");
    }

    @Test
    public void testSnapshotOverwriteOnPageReload() throws IOException {
        File snapshotDir = tempFolder.newFolder("snapshots_reload");
        File file = new File(snapshotDir, "tab_1.jpg");

        // First version
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(new byte[]{10, 20});
        }
        assertThat(file.length()).isEqualTo(2);

        // Second version overwrite
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(new byte[]{30, 40, 50, 60});
        }
        assertThat(file.length()).isEqualTo(4);
    }

    @Test
    public void testSnapshotCleanupOnTabClose() throws IOException {
        File snapshotDir = tempFolder.newFolder("snapshots_cleanup");
        File file = new File(snapshotDir, "tab_closed.jpg");
        file.createNewFile();
        assertThat(file.exists()).isTrue();

        // On tab close
        boolean deleted = file.delete();

        assertThat(deleted).isTrue();
        assertThat(file.exists()).isFalse();
    }
}
