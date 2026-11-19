package com.jestk.browser.engine

import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.Workspace
import org.junit.Test

class TabProfilesTest {
    private val workspaces = Workspace.DEFAULT_WORKSPACES

    @Test
    fun incognitoTab_usesTheIncognitoProfileInEveryWorkspace() {
        val tab = Tab(id = "t", isIncognito = true)

        workspaces.forEach { ws ->
            assertThat(profileNameFor(tab, workspaces, ws.id)).isEqualTo(INCOGNITO_PROFILE_NAME)
        }
    }

    @Test
    fun regularTab_usesTheProfileOfItsWorkspace() {
        val work = workspaces.first { it.id == Workspace.DEFAULT_WORK_ID }

        assertThat(profileNameFor(Tab(id = "t"), workspaces, work.id)).isEqualTo(work.profileName)
    }

    @Test
    fun unknownWorkspace_fallsBackToItsId() {
        assertThat(profileNameFor(null, workspaces, "ghost")).isEqualTo("ghost")
    }

    @Test
    fun incognitoProfileName_isNotAWorkspaceProfile() {
        assertThat(workspaces.map { it.profileName }).doesNotContain(INCOGNITO_PROFILE_NAME)
    }
}
