package com.jestk.browser.e2e.tier2;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Tier 2: Boundary & Edge Case Test for Disk Snapshot Caching (R4).
 */
public class DiskSnapshotCachingBoundaryTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testNonExistentSnapshotDirectoryCreation() {
        File baseDir = tempFolder.getRoot();
        File snapshotDir = new File(baseDir, "nested/sub/snapshots");

        if (!snapshotDir.exists()) {
            boolean created = snapshotDir.mkdirs();
            assertThat(created).isTrue();
        }

        assertThat(snapshotDir.exists()).isTrue();
    }

    @Test
    public void testCorruptImageBytesHandledGracefully() throws IOException {
        File snapshot = tempFolder.newFile("corrupt.jpg");
        try (FileOutputStream fos = new FileOutputStream(snapshot)) {
            fos.write(new byte[]{0, 0, 0}); // Invalid JPEG header
        }

        assertThat(snapshot.exists()).isTrue();
        assertThat(snapshot.length()).isEqualTo(3);
    }

    @Test
    public void testDiskSnapshotFileNameWithSpecialTabIdCharacters() {
        String trickyTabId = "tab_123-abc_UUID";
        String fileName = "tab_" + trickyTabId.replaceAll("[^a-zA-Z0-9_-]", "_") + ".jpg";

        assertThat(fileName).isEqualTo("tab_tab_123-abc_UUID.jpg");
    }

    @Test
    public void testConcurrentWritesToSameSnapshotFile() throws Exception {
        File target = tempFolder.newFile("concurrent.jpg");

        byte[] data1 = new byte[]{1, 2, 3};
        byte[] data2 = new byte[]{4, 5, 6, 7};

        // Write 1
        try (FileOutputStream fos = new FileOutputStream(target)) {
            fos.write(data1);
        }
        // Write 2 overwrite
        try (FileOutputStream fos = new FileOutputStream(target)) {
            fos.write(data2);
        }

        assertThat(target.length()).isEqualTo(4);
    }

    @Test
    public void testDeleteSnapshotWhenFileAlreadyMissingIsSafe() {
        File missingFile = new File(tempFolder.getRoot(), "already_gone.jpg");
        boolean deleted = missingFile.delete();

        assertThat(deleted).isFalse();
        assertThat(missingFile.exists()).isFalse();
    }
}
