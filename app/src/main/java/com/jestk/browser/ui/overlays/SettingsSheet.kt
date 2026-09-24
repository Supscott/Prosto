package com.jestk.browser.ui.overlays

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.res.stringResource
import com.jestk.browser.R
import com.jestk.browser.util.DefaultBrowserHelper
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jestk.browser.ui.theme.LocalIsDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.ui.theme.DarkBackground
import com.jestk.browser.ui.theme.DarkOnSurface
import com.jestk.browser.ui.theme.DarkOnSurfaceVariant
import com.jestk.browser.ui.theme.DarkSurface
import com.jestk.browser.ui.theme.DarkSurfaceVariant
import com.jestk.browser.ui.theme.LocalSereneTypography
import com.jestk.browser.ui.theme.SereneAccent
import com.jestk.browser.ui.theme.SereneBorders
import com.jestk.browser.ui.theme.SereneBrandPrimary
import com.jestk.browser.ui.theme.SereneCanvasFloor
import com.jestk.browser.ui.theme.SereneDarkText
import com.jestk.browser.ui.theme.SereneMutedSlate
import com.jestk.browser.ui.theme.SerenePaleBlue
import com.jestk.browser.ui.theme.SereneShapes
import com.jestk.browser.ui.theme.sereneSkyBackground
import com.jestk.browser.ui.theme.JestkBrowserTheme
import androidx.compose.ui.tooling.preview.Preview
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState

/**
 * Settings Sheet - Liquid Pastel Sunset design system.
 * Features:
 * - Full-height ModalBottomSheet with ambient background glow
 * - Segmented glass theme selector (System, Light, Dark)
 * - Inset grouped cards for Account, Privacy & Security, and About
 * - Stitch liquid pastel sunset switches and badges
 */
@Deprecated(
    message = "SettingsSheet has been refactored into a full screen. Use SettingsScreen in com.jestk.browser.ui.screens",
    replaceWith = ReplaceWith(
        expression = "SettingsScreen(uiState, onEvent, modifier)",
        imports = ["com.jestk.browser.ui.screens.SettingsScreen"]
    )
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDark = LocalIsDarkTheme.current
    val sheetBg = if (isDark) DarkSurface else Color.White

    ModalBottomSheet(
        onDismissRequest = { onEvent(BrowserUiEvent.DismissOverlay) },
        sheetState = sheetState,
        shape = SereneShapes.BottomSheet,
        containerColor = sheetBg,
        scrimColor = Color.Black.copy(alpha = 0.25f),
        modifier = modifier
    ) {
        SettingsSheetContent(
            uiState = uiState,
            onEvent = onEvent
        )
    }
}

@Composable
fun SettingsSheetContent(
    uiState: BrowserUiState,
    onEvent: (BrowserUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val defaultBrowserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onEvent(BrowserUiEvent.CheckDefaultBrowser(context))
    }
    val scrollState = rememberScrollState()
    val isDark = LocalIsDarkTheme.current
    var isTrackingProtectionEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState)
            .padding(bottom = 36.dp)
    ) {
        // Unified Header Bar with Title and Close Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                style = LocalSereneTypography.current.headlineMd,
                color = if (isDark) DarkOnSurface else SereneDarkText
            )

            IconButton(
                onClick = { onEvent(BrowserUiEvent.DismissOverlay) },
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isDark) DarkSurfaceVariant else SereneCanvasFloor)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = SereneMutedSlate,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // SECTION 1: THEME PICKER (Segmented Control Container)
        SettingsSectionHeader(title = "APPEARANCE")
        SegmentedThemeControl(
            selectedMode = uiState.settings.themeMode,
            isDark = isDark,
            onSelectMode = { onEvent(BrowserUiEvent.SetThemeMode(it)) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION 2: ACCOUNT
        SettingsSectionHeader(title = "ACCOUNT")
        InsetGroupedCard(isDark = isDark) {
            // Profile Hero Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        Toast.makeText(context, "Account: sync@jestk.com", Toast.LENGTH_SHORT).show()
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isDark) DarkSurfaceVariant else SerenePaleBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = SereneBrandPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "User Account",
                        style = LocalSereneTypography.current.bodyMd.copy(fontWeight = FontWeight.Bold),
                        color = if (isDark) DarkOnSurface else SereneDarkText
                    )
                    Text(
                        text = "sync@jestk.com",
                        style = LocalSereneTypography.current.bodySm,
                        color = SereneMutedSlate
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = SereneMutedSlate,
                    modifier = Modifier.size(20.dp)
                )
            }

            HorizontalDivider(
                color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                modifier = Modifier.padding(start = 74.dp)
            )

            // Sync Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        Toast.makeText(context, "Sync is active across devices", Toast.LENGTH_SHORT).show()
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDark) DarkSurfaceVariant else SereneCanvasFloor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = SereneBrandPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sync Tabs & Bookmarks",
                        style = LocalSereneTypography.current.bodyMd.copy(fontWeight = FontWeight.Medium),
                        color = if (isDark) DarkOnSurface else SereneDarkText
                    )
                    Text(
                        text = "Active across connected devices",
                        style = LocalSereneTypography.current.bodySm,
                        color = SereneMutedSlate
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "On",
                        style = LocalSereneTypography.current.bodyMd.copy(fontWeight = FontWeight.SemiBold),
                        color = SereneBrandPrimary
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = SereneMutedSlate,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION: DEFAULT BROWSER (Only visible if not default)
        if (!uiState.isDefaultBrowser) {
            SettingsSectionHeader(title = "GENERAL")
            InsetGroupedCard(isDark = isDark) {
                DefaultBrowserSettingTile(
                    isDark = isDark,
                    onClick = {
                        DefaultBrowserHelper.openDefaultBrowserSettings(context, defaultBrowserLauncher)
                    }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // SECTION 3: PRIVACY & SECURITY
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsSectionHeader(title = "PRIVACY & PROTECTION", modifier = Modifier.padding(bottom = 0.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) DarkSurfaceVariant else SerenePaleBlue
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = SereneBrandPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Protected",
                        style = LocalSereneTypography.current.labelMd.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = SereneBrandPrimary
                    )
                }
            }
        }

        InsetGroupedCard(isDark = isDark) {
            // Ad Blocker Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) DarkSurfaceVariant else SereneCanvasFloor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = SereneBrandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Ad Blocker",
                            style = LocalSereneTypography.current.bodyMd.copy(fontWeight = FontWeight.Medium),
                            color = if (isDark) DarkOnSurface else SereneDarkText
                        )
                        Text(
                            text = "Eliminates banners and popups",
                            style = LocalSereneTypography.current.bodySm,
                            color = SereneMutedSlate
                        )
                    }
                }

                LiquidSunsetSwitch(
                    checked = uiState.settings.isAdBlockerEnabled,
                    onCheckedChange = { onEvent(BrowserUiEvent.SetAdBlockerEnabled(it)) }
                )
            }

            HorizontalDivider(
                color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                modifier = Modifier.padding(start = 64.dp)
            )

            // Tracking Protection Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) DarkSurfaceVariant else SereneCanvasFloor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = SereneBrandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Tracking Protection",
                            style = LocalSereneTypography.current.bodyMd.copy(fontWeight = FontWeight.Medium),
                            color = if (isDark) DarkOnSurface else SereneDarkText
                        )
                        Text(
                            text = "Prevents cross-site tracking",
                            style = LocalSereneTypography.current.bodySm,
                            color = SereneMutedSlate
                        )
                    }
                }

                LiquidSunsetSwitch(
                    checked = isTrackingProtectionEnabled,
                    onCheckedChange = { isTrackingProtectionEnabled = it }
                )
            }

            HorizontalDivider(
                color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                modifier = Modifier.padding(start = 64.dp)
            )

            // Search Engine Row with Dropdown Menu
            var searchMenuExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { searchMenuExpanded = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) DarkSurfaceVariant else SereneCanvasFloor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = SereneBrandPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Search Engine",
                            style = LocalSereneTypography.current.bodyMd.copy(fontWeight = FontWeight.Medium),
                            color = if (isDark) DarkOnSurface else SereneDarkText
                        )
                        Text(
                            text = "Default search provider",
                            style = LocalSereneTypography.current.bodySm,
                            color = SereneMutedSlate
                        )
                    }
                }

                Box {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) DarkSurfaceVariant else SereneCanvasFloor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { searchMenuExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = uiState.settings.searchEngine.displayName,
                                style = LocalSereneTypography.current.labelMd.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = SereneBrandPrimary
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Search Engine",
                                tint = SereneBrandPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = searchMenuExpanded,
                        onDismissRequest = { searchMenuExpanded = false }
                    ) {
                        SearchEngine.entries.forEach { engine ->
                            val selected = uiState.settings.searchEngine == engine
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = engine.displayName,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) SereneBrandPrimary else (if (isDark) DarkOnSurface else SereneDarkText)
                                    )
                                },
                                trailingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = SereneBrandPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    searchMenuExpanded = false
                                    onEvent(BrowserUiEvent.SetSearchEngine(engine))
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION 4: ABOUT
        SettingsSectionHeader(title = "ABOUT HORI BROWSER")
        InsetGroupedCard(isDark = isDark) {
            HyperlinkRow(
                title = "Hori Browser",
                subtitle = "Version 1.0.0 · jestk.com",
                onClick = {
                    onEvent(BrowserUiEvent.OpenNewTab("https://jestk.com"))
                    onEvent(BrowserUiEvent.DismissOverlay)
                }
            )

            HorizontalDivider(
                color = if (isDark) DarkSurfaceVariant else Color(0xFFF1F5F9),
                modifier = Modifier.padding(start = 16.dp)
            )

            HyperlinkRow(
                title = "Source Code on GitHub",
                subtitle = "github.com/jestk/browser",
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com"))
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Footer Note
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (isDark) DarkSurfaceVariant else SerenePaleBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = SereneBrandPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = "Crafted with love for a fast and private web",
                style = LocalSereneTypography.current.bodySm,
                color = SereneMutedSlate
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = LocalSereneTypography.current.labelMd.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        ),
        color = SereneMutedSlate,
        modifier = modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
private fun InsetGroupedCard(
    isDark: Boolean,
    content: @Composable () -> Unit
) {
    Surface(
        shape = SereneShapes.Card,
        color = if (isDark) DarkSurface else Color.White,
        border = SereneBorders.cardBorder(isDark),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun SegmentedThemeControl(
    selectedMode: ThemeMode,
    isDark: Boolean,
    onSelectMode: (ThemeMode) -> Unit
) {
    Surface(
        shape = SereneShapes.Card,
        color = if (isDark) DarkSurface else Color.White,
        border = SereneBorders.cardBorder(isDark),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ThemeMode.entries.forEach { mode ->
                val isSelected = selectedMode == mode
                val segmentBg = if (isSelected) {
                    if (isDark) DarkSurfaceVariant else SerenePaleBlue
                } else Color.Transparent

                val segmentBorder = if (isSelected) {
                    BorderStroke(1.5.dp, SereneBrandPrimary)
                } else null

                val tint = if (isSelected) SereneBrandPrimary else SereneMutedSlate

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = segmentBg,
                    border = segmentBorder,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectMode(mode) }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val icon: ImageVector = when (mode) {
                            ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                            ThemeMode.LIGHT -> Icons.Default.LightMode
                            ThemeMode.DARK -> Icons.Default.DarkMode
                        }
                        val label = when (mode) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        }

                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = tint,
                            modifier = Modifier.size(19.dp)
                        )
                        Text(
                            text = label,
                            style = LocalSereneTypography.current.labelSm.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = tint
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidSunsetSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = tween(durationMillis = 220),
        label = "SwitchThumbTranslation"
    )

    Box(
        modifier = modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(CircleShape)
            .background(
                if (checked) SereneBrandPrimary
                else (if (isDark) DarkSurfaceVariant else Color(0xFFE2E8F0))
            )
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White,
            border = BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.08f)),
            shadowElevation = 1.dp,
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(27.dp)
        ) {}
    }
}

@Composable
private fun HyperlinkRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                style = LocalSereneTypography.current.bodyMd.copy(fontWeight = FontWeight.Medium),
                color = if (isDark) DarkOnSurface else SereneDarkText
            )
            Text(
                text = subtitle,
                style = LocalSereneTypography.current.bodySm,
                color = SereneMutedSlate
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = SereneMutedSlate,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun DefaultBrowserSettingTile(
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDark) DarkSurfaceVariant else SerenePaleBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = SereneBrandPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = stringResource(R.string.default_browser_tile_title),
                    style = LocalSereneTypography.current.bodyMd.copy(fontWeight = FontWeight.Medium),
                    color = if (isDark) DarkOnSurface else SereneDarkText
                )
                Text(
                    text = stringResource(R.string.default_browser_tile_desc),
                    style = LocalSereneTypography.current.bodySm,
                    color = SereneMutedSlate
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isDark) DarkSurfaceVariant else SerenePaleBlue
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.default_browser_tile_action),
                    style = LocalSereneTypography.current.labelSm.copy(fontWeight = FontWeight.SemiBold),
                    color = SereneBrandPrimary
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = SereneBrandPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Preview(name = "SettingsSheet Light", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun SettingsSheetLightPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.LIGHT) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SereneCanvasFloor)
        ) {
            SettingsSheetContent(
                uiState = BrowserUiState(),
                onEvent = {}
            )
        }
    }
}

@Preview(name = "SettingsSheet Dark", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
fun SettingsSheetDarkPreview() {
    JestkBrowserTheme(themeMode = ThemeMode.DARK) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
        ) {
            SettingsSheetContent(
                uiState = BrowserUiState(),
                onEvent = {}
            )
        }
    }
}
