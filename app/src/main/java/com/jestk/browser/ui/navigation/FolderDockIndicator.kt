package com.jestk.browser.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.R
import com.jestk.browser.domain.model.TabFolder
import com.jestk.browser.ui.theme.LocalProstoTypography

/**
 * Calculates high-contrast text and icon color for a given folder background color.
 */
fun getContrastingFolderContentColor(bgColor: Color): Color {
    val luminance = 0.299f * bgColor.red + 0.587f * bgColor.green + 0.114f * bgColor.blue
    return if (luminance > 0.55f) Color(0xFF0F172A) else Color.White
}

/**
 * Folder indicator rendered in the dock (TabStripRow).
 * - When [folder.title] is blank: 38dp circle with folder color and directional arrow.
 * - When [folder.title] is not blank: rounded chip with folder color, title, and directional arrow.
 * - Directional arrow points left (←) when expanded and right (→) when collapsed.
 */
@Composable
fun FolderDockIndicator(
    folder: TabFolder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val folderBgColor = Color(folder.color)
    val contentColor = getContrastingFolderContentColor(folderBgColor)
    val typography = LocalProstoTypography.current

    val arrowIcon = if (folder.isCollapsed) {
        Icons.AutoMirrored.Filled.ArrowForward
    } else {
        Icons.AutoMirrored.Filled.ArrowBack
    }
    val arrowDescription = stringResource(
        if (folder.isCollapsed) R.string.folder_expand else R.string.folder_collapse
    )

    if (folder.title.isBlank()) {
        Surface(
            shape = CircleShape,
            color = folderBgColor,
            shadowElevation = 0.dp,
            modifier = modifier
                .size(38.dp)
                .clip(CircleShape)
                .testTag("folder_indicator_${folder.id}")
                .clickable(onClick = onClick),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = arrowIcon,
                    contentDescription = arrowDescription,
                    tint = contentColor,
                    modifier = Modifier
                        .size(18.dp)
                        .testTag("folder_arrow_${folder.id}"),
                )
            }
        }
    } else {
        Surface(
            shape = CircleShape,
            color = folderBgColor,
            shadowElevation = 0.dp,
            modifier = modifier
                .height(38.dp)
                .clip(CircleShape)
                .testTag("folder_indicator_${folder.id}")
                .clickable(onClick = onClick),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = folder.title,
                    style = typography.labelMd.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    ),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("folder_title_${folder.id}"),
                )
                Icon(
                    imageVector = arrowIcon,
                    contentDescription = arrowDescription,
                    tint = contentColor,
                    modifier = Modifier
                        .size(16.dp)
                        .testTag("folder_arrow_${folder.id}"),
                )
            }
        }
    }
}
