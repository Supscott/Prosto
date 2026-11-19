package com.jestk.browser.engine

import androidx.webkit.Profile
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.Workspace
import org.junit.Test

class ProfileCleanupTest {
    @Test
    fun leftovers_areIncognitoProfilesAndPendingWorkspaceProfiles() {
        val all = listOf(
            Profile.DEFAULT_PROFILE_NAME,
            Workspace.DEFAULT_WORK_ID,
            "ws_1_abc",
            "ws_2_def",
            "incognito-0",
            "incognito-3",
        )

        assertThat(leftoverProfiles(all, pending = setOf("ws_1_abc")))
            .containsExactly("ws_1_abc", "incognito-0", "incognito-3")
    }

    @Test
    fun defaultProfile_isNeverALeftover() {
        assertThat(leftoverProfiles(listOf(Profile.DEFAULT_PROFILE_NAME), pending = setOf(Profile.DEFAULT_PROFILE_NAME)))
            .isEmpty()
    }

    @Test
    fun pendingNameMissingFromTheStore_isNotDeleted() {
        assertThat(leftoverProfiles(listOf(Workspace.DEFAULT_WORK_ID), pending = setOf("ws_gone"))).isEmpty()
    }
}
