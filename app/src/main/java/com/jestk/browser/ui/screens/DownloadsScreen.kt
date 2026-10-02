package com.jestk.browser.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.domain.model.DownloadStatus
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.engine.download.DownloadProgressInfo
import com.jestk.browser.ui.theme.DarkBackground
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.JestkBrowserTheme
import com.jestk.browser.ui.theme.LocalSereneTypography
import com.jestk.browser.ui.theme.SereneBorders
import com.jestk.browser.ui.theme.SereneBrandPrimary
import com.jestk.browser.ui.theme.SereneCanvasFloor
import com.jestk.browser.ui.theme.SereneDarkText
import com.jestk.browser.ui.theme.SereneMutedSlate
import com.jestk.browser.ui.theme.SerenePaleBlue
import com.jestk.browser.ui.theme.SereneShapes
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DownloadsScreen — Dedicated, separate full screen for managing file downloads.
 * Conforms strictly to Serene Canvas design tokens with solid surfaces,
 * elevation shadows, rim borders, and ZERO BLUR.
 */
@Composable
fun DownloadsScreen(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = uiState.settings.themeMode == ThemeMode.DARK
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) DarkBackground else SereneCanvasFloor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        // Top Bar: Back Button, Screen Title, Close Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onEvent(BrowserUiEvent.HandleBackPress) },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = "Back",
                    tint = if (isDark) DarkOnSurface else SereneDarkText,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = "Downloads",
                style = LocalSereneTypography.current.headlineSm,
                color = if (isDark) DarkOnSurface else SereneDarkText
            )

            IconButton(
                onClick = { onEvent(BrowserUiEvent.HandleBackPress) },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = SereneMutedSlate,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Large Title Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Downloads",
                style = LocalSereneTypography.current.headlineLg,
                color = if (isDark) DarkOnSurface else SereneDarkText
            )

            if (uiState.downloads.isNotEmpty()) {
                Surface(
                    shape = SereneShapes.Pill,
                    color = if (isDark) DarkSurfaceVariant else SerenePaleBlue
                ) {
                    Text(
                        text = "${uiState.downloads.size} ${if (uiState.downloads.size == 1) "file" else "files"}",
                        style = LocalSereneTypography.current.labelSm.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = SereneBrandPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content: Empty state or List of Downloads
        if (uiState.downloads.isEmpty()) {
            DownloadsEmptyState(isDark = isDark)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = uiState.downloads,
                    key = { it.id }
                ) { item ->
                    val progressInfo = uiState.downloadProgressMap[item.id]

                    DownloadItemCard(
                        item = item,
                        progressInfo = progressInfo,
                        isDark = isDark,
                        onClick = {
                            val currentStatus = progressInfo?.status ?: item.status
                            if (currentStatus == DownloadStatus.DOWNLOADING || currentStatus == DownloadStatus.PENDING || currentStatus == DownloadStatus.PAUSED) {
                                Toast.makeText(context, "Download in progress...", Toast.LENGTH_SHORT).show()
                            } else if (currentStatus == DownloadStatus.FAILED) {
                                Toast.makeText(context, "Download failed. Tap retry to restart.", Toast.LENGTH_SHORT).show()
                            } else {
                                openDownloadedFile(context, item)
                            }
                        },
                        onPause = { onEvent(BrowserUiEvent.PauseDownload(item.id)) },
                        onResume = { onEvent(BrowserUiEvent.ResumeDownload(item.id)) },
                        onCancel = { onEvent(BrowserUiEvent.CancelDownload(item.id)) },
                        onRetry = { onEvent(BrowserUiEvent.RetryDownload(item.id)) },
                        onDelete = { onEvent(BrowserUiEvent.DeleteDownload(item.id, deleteFile = true)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadsEmptyState(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    if (isDark) DarkSurfaceVariant
                    else SerenePaleBlue
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = SereneBrandPrimary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "No downloads yet",
            style = LocalSereneTypography.current.headlineSm,
            color = if (isDark) DarkOnSurface else SereneDarkText
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Files downloaded from websites will appear here",
            style = LocalSereneTypography.current.bodyMd,
            color = SereneMutedSlate
        )
    }
}

@Composable
private fun DownloadItemCard(
    item: DownloadItem,
    progressInfo: DownloadProgressInfo? = null,
    isDark: Boolean,
    onClick: () -> Unit,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onCancel: () -> Unit = {},
    onRetry: () -> Unit = {},
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val category = resolveFileCategory(item.fileName, item.mimeType)

    val currentStatus = progressInfo?.status ?: item.status
    val isDownloading = currentStatus == DownloadStatus.DOWNLOADING || currentStatus == DownloadStatus.PENDING
    val isPaused = currentStatus == DownloadStatus.PAUSED
    val isFailed = currentStatus == DownloadStatus.FAILED
    val bytesDownloaded = progressInfo?.bytesDownloaded ?: item.bytesDownloaded
    val totalBytes = progressInfo?.totalBytes ?: item.totalBytes

    Surface(
        shape = SereneShapes.Card,
        color = if (isDark) DarkSurface else Color.White,
        border = SereneBorders.cardBorder(isDark),
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Category Icon Badge
            CategoryIconBadge(category = category, isDark = isDark)

            // File Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.fileName.ifEmpty { "download" },
                    style = LocalSereneTypography.current.bodyMd.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (isDark) DarkOnSurface else SereneDarkText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when {
                        isDownloading -> {
                            val progressText = formatProgressText(bytesDownloaded, totalBytes)
                            Text(
                                text = progressText,
                                style = LocalSereneTypography.current.bodySm.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = SereneBrandPrimary
                            )
                            if (progressInfo?.speedBytesPerSec != null && progressInfo.speedBytesPerSec > 0) {
                                Text(
                                    text = "·",
                                    style = LocalSereneTypography.current.bodySm.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = SereneMutedSlate
                                )
                                Text(
                                    text = "${formatFileSize(progressInfo.speedBytesPerSec)}/s",
                                    style = LocalSereneTypography.current.bodySm.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = SereneMutedSlate
                                )
                            }
                        }
                        isPaused -> {
                            val progressText = formatProgressText(bytesDownloaded, totalBytes)
                            Text(
                                text = "Paused · $progressText",
                                style = LocalSereneTypography.current.bodySm.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color(0xFFF59E0B)
                            )
                        }
                        isFailed -> {
                            Text(
                                text = "Download failed",
                                style = LocalSereneTypography.current.bodySm.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color(0xFFEF4444)
                            )
                        }
                        else -> {
                            val sizeStr = formatFileSize(if (totalBytes > 0L) totalBytes else item.contentLength)
                            if (sizeStr.isNotEmpty()) {
                                Text(
                                    text = sizeStr,
                                    style = LocalSereneTypography.current.bodySm.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = SereneMutedSlate
                                )
                            }

                            if (item.timestamp > 0) {
                                if (sizeStr.isNotEmpty()) {
                                    Text(
                                        text = "·",
                                        style = LocalSereneTypography.current.bodySm.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = SereneMutedSlate
                                    )
                                }
                                Text(
                                    text = formatTimestamp(item.timestamp),
                                    style = LocalSereneTypography.current.bodySm,
                                    color = SereneMutedSlate
                                )
                            }
                        }
                    }
                }

                if (isDownloading || isPaused) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val fraction = if (totalBytes > 0L) (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else null
                    if (fraction != null) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = if (isPaused) Color(0xFFF59E0B) else SereneBrandPrimary,
                            trackColor = if (isDark) DarkSurfaceVariant else SerenePaleBlue
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = if (isPaused) Color(0xFFF59E0B) else SereneBrandPrimary,
                            trackColor = if (isDark) DarkSurfaceVariant else SerenePaleBlue
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when {
                    isDownloading -> {
                        IconButton(
                            onClick = onPause,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Pause download",
                                tint = SereneBrandPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel download",
                                tint = SereneMutedSlate,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    isPaused -> {
                        IconButton(
                            onClick = onResume,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Resume download",
                                tint = SereneBrandPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel download",
                                tint = SereneMutedSlate,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    isFailed -> {
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry download",
                                tint = SereneBrandPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete download",
                                tint = SereneMutedSlate,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    else -> {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete download",
                                tint = SereneMutedSlate,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryIconBadge(
    category: FileCategory,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val (icon, bgLight, bgDark, tintLight, tintDark) = when (category) {
        FileCategory.IMAGE -> CategoryStyle(
            Icons.Default.Image,
            Color(0xFFE0F2FE),
            Color(0xFF0C4A6E),
            Color(0xFF0284C7),
            Color(0xFF38BDF8)
        )
        FileCategory.VIDEO -> CategoryStyle(
            Icons.Default.VideoFile,
            SerenePaleBlue,
            Color(0xFF1E3A8A),
            SereneBrandPrimary,
            Color(0xFF93C5FD)
        )
        FileCategory.AUDIO -> CategoryStyle(
            Icons.Default.AudioFile,
            Color(0xFFFCE7F3),
            Color(0xFF831843),
            Color(0xFFDB2777),
            Color(0xFFF472B6)
        )
        FileCategory.DOCUMENT -> CategoryStyle(
            Icons.Default.Description,
            Color(0xFFFEF3C7),
            Color(0xFF78350F),
            Color(0xFFD97706),
            Color(0xFFFBBF24)
        )
        FileCategory.ARCHIVE -> CategoryStyle(
            Icons.Default.FolderZip,
            Color(0xFFEDE9FE),
            Color(0xFF4C1D95),
            Color(0xFF7C3AED),
            Color(0xFFA78BFA)
        )
        FileCategory.APK -> CategoryStyle(
            Icons.Default.Android,
            Color(0xFFDCFCE7),
            Color(0xFF14532D),
            Color(0xFF16A34A),
            Color(0xFF4ADE80)
        )
        FileCategory.OTHER -> CategoryStyle(
            Icons.AutoMirrored.Filled.InsertDriveFile,
            SerenePaleBlue,
            DarkSurfaceVariant,
            SereneMutedSlate,
            SereneMutedSlate
        )
    }

    Box(
        modifier = modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) bgDark else bgLight),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDark) tintDark else tintLight,
            modifier = Modifier.size(22.dp)
        )
    }
}

private data class CategoryStyle(
    val icon: ImageVector,
    val bgLight: Color,
    val bgDark: Color,
    val tintLight: Color,
    val tintDark: Color
)

private enum class FileCategory {
    IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APK, OTHER
}

private fun resolveFileCategory(fileName: String, mimeType: String?): FileCategory {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val mime = mimeType?.lowercase(Locale.ROOT) ?: ""

    return when {
        mime.startsWith("image/") || ext in setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico") ->
            FileCategory.IMAGE

        mime.startsWith("video/") || ext in setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv") ->
            FileCategory.VIDEO

        mime.startsWith("audio/") || ext in setOf("mp3", "m4a", "wav", "ogg", "flac", "aac", "opus", "wma") ->
            FileCategory.AUDIO

        mime.contains("pdf") || ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv") ->
            FileCategory.DOCUMENT

        mime.contains("zip") || mime.contains("tar") || mime.contains("rar") ||
                ext in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz") ->
            FileCategory.ARCHIVE

        mime.contains("android.package-archive") || ext == "apk" ->
            FileCategory.APK

        else -> FileCategory.OTHER
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

fun formatProgressText(bytesDownloaded: Long, totalBytes: Long): String {
    val downloadedStr = formatFileSize(bytesDownloaded).ifEmpty { "0 B" }
    return if (totalBytes > 0L) {
        val totalStr = formatFileSize(totalBytes)
        val percent = ((bytesDownloaded * 100) / totalBytes).coerceIn(0, 100)
        "$downloadedStr / $totalStr ($percent%)"
    } else {
        downloadedStr
    }
}

fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun openDownloadedFile(context: Context, download: DownloadItem) {
    try {
        val file = File(download.filePath)
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist: ${download.fileName}", Toast.LENGTH_SHORT).show()
            return
        }

        val uri: Uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (_: Exception) {
            Uri.fromFile(file)
        }

        val mime = download.mimeType
            ?: getMimeTypeFromExtension(download.fileName)
            ?: "*/*"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, "Open with")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun getMimeTypeFromExtension(fileName: String): String? {
    val ext = fileName.substringAfterLast('.', "")
    if (ext.isEmpty()) return null
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase(Locale.ROOT))
}

@Preview(name = "DownloadsScreen Light", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun DownloadsScreenLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        DownloadsScreen(
            uiState = BrowserUiState(),
            onEvent = {}
        )
    }
}

@Preview(name = "DownloadsScreen Dark", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun DownloadsScreenDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        DownloadsScreen(
            uiState = BrowserUiState(),
            onEvent = {}
        )
    }
}
