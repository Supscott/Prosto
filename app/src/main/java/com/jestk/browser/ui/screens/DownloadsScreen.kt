package com.jestk.browser.ui.overlays

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.ui.theme.LiquidOutline
import com.jestk.browser.ui.theme.LiquidPrimary
import com.jestk.browser.ui.theme.LiquidPrimaryFixed
import com.jestk.browser.ui.theme.LiquidSecondary
import com.jestk.browser.ui.theme.LiquidSecondaryFixed
import com.jestk.browser.ui.theme.LiquidSurface
import com.jestk.browser.ui.theme.LiquidSurfaceContainerHigh
import com.jestk.browser.ui.theme.LiquidTertiary
import com.jestk.browser.ui.theme.LiquidTertiaryFixed
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import com.jestk.browser.ui.theme.PastelPeach
import com.jestk.browser.ui.theme.PastelRose
import com.jestk.browser.ui.theme.LiquidShapes
import com.jestk.browser.ui.theme.LiquidBorders
import com.jestk.browser.ui.theme.liquidFrostedGlass
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FileCategory {
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    ARCHIVE,
    APK,
    GENERIC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF161314) else Color(0xFFFFF8F8))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        DownloadsContent(uiState = uiState, onEvent = onEvent, modifier = Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsSheet(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = LocalIsDarkTheme.current
    val sheetBg = if (isDark) Color(0xFF1E1B1C).copy(alpha = 0.85f) else Color(0xFFFFF8F8).copy(alpha = 0.85f)

    ModalBottomSheet(
        onDismissRequest = { onEvent(BrowserUiEvent.DismissOverlay) },
        sheetState = sheetState,
        shape = LiquidShapes.BottomSheet,
        containerColor = sheetBg,
        modifier = modifier
    ) {
        DownloadsContent(
            uiState = uiState,
            onEvent = onEvent
        )
    }
}

@Composable
fun DownloadsContent(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        // Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onEvent(BrowserUiEvent.DismissOverlay) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = "Downloads",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            IconButton(
                onClick = { onEvent(BrowserUiEvent.DismissOverlay) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Large Title Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Downloads",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (uiState.downloads.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f) else LiquidPrimaryFixed.copy(alpha = 0.60f)
                ) {
                    Text(
                        text = "${uiState.downloads.size} ${if (uiState.downloads.size == 1) "file" else "files"}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
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
                    val isInProgress = item.id in uiState.activeDownloadIds ||
                            (item.id == uiState.lastDownloadNotification?.id && uiState.showDownloadSnackbar) ||
                            (!File(item.filePath).exists() && (System.currentTimeMillis() - item.timestamp < 30_000))

                    DownloadItemCard(
                        item = item,
                        isInProgress = isInProgress,
                        isDark = isDark,
                        onClick = {
                            if (isInProgress && !File(item.filePath).exists()) {
                                Toast.makeText(context, "Download in progress...", Toast.LENGTH_SHORT).show()
                            } else {
                                openDownloadedFile(context, item)
                            }
                        },
                        onDelete = { onEvent(BrowserUiEvent.DeleteDownload(item.id)) }
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
                    if (isDark) Color(0xFF2B2527)
                    else LiquidSecondaryFixed.copy(alpha = 0.60f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = if (isDark) PastelPeach else LiquidSecondary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "No downloads yet",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Files downloaded from websites will appear here",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DownloadItemCard(
    item: DownloadItem,
    isInProgress: Boolean = false,
    isDark: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val category = resolveFileCategory(item.fileName, item.mimeType)
    val cardBg = if (isDark) Color(0xFF1E1B1C).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.90f)
    val cardBorder = if (isDark) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.85f))

    var downloadProgress by remember(item.downloadId, item.id) {
        mutableStateOf<Float?>(null)
    }
    var activeInProgress by remember(isInProgress, item.id) {
        mutableStateOf(isInProgress)
    }

    LaunchedEffect(item.downloadId, isInProgress) {
        if (item.downloadId != -1L) {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (dm != null) {
                try {
                    val cursor = dm.query(DownloadManager.Query().setFilterById(item.downloadId))
                    if (cursor != null && cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = if (statusIndex != -1) cursor.getInt(statusIndex) else -1
                        val bytesSoFarIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val totalBytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val soFar = if (bytesSoFarIdx != -1) cursor.getLong(bytesSoFarIdx) else 0L
                        val total = if (totalBytesIdx != -1) cursor.getLong(totalBytesIdx) else 0L
                        cursor.close()

                        if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) {
                            activeInProgress = true
                            if (total > 0L) {
                                downloadProgress = (soFar.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            }
                        } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            activeInProgress = false
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    Surface(
        shape = LiquidShapes.Card,
        color = Color.Transparent,
        border = null,
        shadowElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .liquidFrostedGlass(
                shape = LiquidShapes.Card,
                isDark = isDark,
                blurRadius = 24.dp,
                tintColor = if (isDark) Color(0xFF1E1B1C) else Color.White,
                alpha = if (isDark) 0.55f else 0.50f,
            )
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
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (activeInProgress) {
                        Text(
                            text = "Downloading...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = LiquidPrimary
                        )
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    val sizeStr = formatFileSize(item.contentLength)
                    if (sizeStr.isNotEmpty()) {
                        Text(
                            text = sizeStr,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    if (item.timestamp > 0) {
                        if (sizeStr.isNotEmpty()) {
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Text(
                            text = formatTimestamp(item.timestamp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                if (activeInProgress) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val p = downloadProgress
                    if (p != null) {
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = LiquidPrimary,
                            trackColor = if (isDark) Color(0xFF352F31) else LiquidPrimaryFixed.copy(alpha = 0.35f)
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = LiquidPrimary,
                            trackColor = if (isDark) Color(0xFF352F31) else LiquidPrimaryFixed.copy(alpha = 0.35f)
                        )
                    }
                }
            }

            // Delete Button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete download",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
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
            LiquidSecondaryFixed,
            Color(0xFF532E20),
            LiquidSecondary,
            PastelPeach
        )
        FileCategory.VIDEO -> CategoryStyle(
            Icons.Default.VideoFile,
            LiquidPrimaryFixed,
            Color(0xFF1B4975),
            LiquidPrimary,
            Color(0xFF9ECAFC)
        )
        FileCategory.AUDIO -> CategoryStyle(
            Icons.Default.AudioFile,
            LiquidTertiaryFixed,
            Color(0xFF5A2A3B),
            LiquidTertiary,
            PastelRose
        )
        FileCategory.DOCUMENT -> CategoryStyle(
            Icons.Default.Description,
            Color(0xFFFEF3C7),
            Color(0xFF4C3E18),
            Color(0xFFB45309),
            Color(0xFFFDE68A)
        )
        FileCategory.ARCHIVE -> CategoryStyle(
            Icons.Default.FolderZip,
            Color(0xFFEDE9FE),
            Color(0xFF3B2E58),
            Color(0xFF6D28D9),
            Color(0xFFDDD6FE)
        )
        FileCategory.APK -> CategoryStyle(
            Icons.Default.Android,
            Color(0xFFD1FAE5),
            Color(0xFF164E3A),
            Color(0xFF059669),
            Color(0xFFA7F3D0)
        )
        FileCategory.GENERIC -> CategoryStyle(
            Icons.AutoMirrored.Filled.InsertDriveFile,
            LiquidSurfaceContainerHigh,
            Color(0xFF2C2829),
            LiquidOutline,
            Color(0xFFD0C5C6)
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

fun resolveFileCategory(fileName: String, mimeType: String?): FileCategory {
    val mime = mimeType?.lowercase(Locale.ROOT) ?: ""
    val name = fileName.lowercase(Locale.ROOT)
    val extension = name.substringAfterLast('.', "")

    return when {
        mime.startsWith("image/") || extension in listOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp", "ico", "avif") -> FileCategory.IMAGE
        mime.startsWith("video/") || extension in listOf("mp4", "mkv", "webm", "avi", "mov", "flv", "3gp") -> FileCategory.VIDEO
        mime.startsWith("audio/") || extension in listOf("mp3", "wav", "ogg", "m4a", "flac", "aac", "opus") -> FileCategory.AUDIO
        mime == "application/pdf" || extension == "pdf" ||
                mime.startsWith("text/") || extension in listOf("txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv", "json", "xml", "html", "md") -> FileCategory.DOCUMENT
        extension in listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz") || mime.contains("zip") || mime.contains("compressed") || mime.contains("tar") -> FileCategory.ARCHIVE
        extension == "apk" || mime == "application/vnd.android.package-archive" -> FileCategory.APK
        else -> FileCategory.GENERIC
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
