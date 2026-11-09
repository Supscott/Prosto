package com.jestk.browser.domain.model

data class Workspace(
    val id: String,
    val name: String,
    val color: Int,
    val position: Int = 0,
    val profileName: String = id,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val DEFAULT_PERSONAL_ID = "default_personal"
        const val DEFAULT_WORK_ID = "default_work"

        val DEFAULT_PERSONAL = Workspace(
            id = DEFAULT_PERSONAL_ID,
            name = "Personal",
            color = 0xFF2196F3.toInt(), // Blue from Prosto palette
            position = 0,
            profileName = DEFAULT_PERSONAL_ID,
            createdAt = 0L,
        )

        val DEFAULT_WORK = Workspace(
            id = DEFAULT_WORK_ID,
            name = "Work",
            color = 0xFFF59E0B.toInt(), // Amber from Prosto palette
            position = 1,
            profileName = DEFAULT_WORK_ID,
            createdAt = 0L,
        )

        val DEFAULT_WORKSPACES = listOf(DEFAULT_PERSONAL, DEFAULT_WORK)
    }
}
