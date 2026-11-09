package com.jestk.browser.domain.model

data class TabFolder(
    val id: String,
    val title: String = "",
    val color: Int,
    val isCollapsed: Boolean = false,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val workspaceId: String = Workspace.DEFAULT_PERSONAL_ID,
) {
    companion object {
        // Curated 8-color Prosto aesthetic palette (ARGB Ints)
        val PALETTE = listOf(
            0xFF2196F3.toInt(), // Blue
            0xFF00BCD4.toInt(), // Cyan
            0xFF10B981.toInt(), // Emerald
            0xFFF59E0B.toInt(), // Amber
            0xFFFF6F61.toInt(), // Coral
            0xFFF43F5E.toInt(), // Rose
            0xFF8B5CF6.toInt(), // Violet
            0xFF6B7280.toInt(), // Graphite
        )
    }
}
