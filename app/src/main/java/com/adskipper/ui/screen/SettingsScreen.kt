package com.adskipper.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adskipper.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onResetStats: () -> Unit,
    onDeleteAllRules: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    var showResetStatsDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // General Settings
            SettingsSectionTitle("通用设置")

            SettingsSwitchItem(
                icon = Icons.Outlined.Notifications,
                title = "显示提示",
                subtitle = "跳过广告时显示 Toast 提示",
                checked = settings.showToast,
                onCheckedChange = {
                    viewModel.setShowToast(it)
                }
            )

            SettingsSwitchItem(
                icon = Icons.Outlined.Vibration,
                title = "振动反馈",
                subtitle = "跳过广告时振动提示",
                checked = settings.vibrate,
                onCheckedChange = {
                    viewModel.setVibrate(it)
                }
            )

            // Skip delay
            var delayValue by remember(settings.skipDelay) {
                mutableFloatStateOf(settings.skipDelay.toFloat())
            }
            SettingsSliderItem(
                icon = Icons.Outlined.Timer,
                title = "跳过延迟",
                subtitle = "检测到广告后等待 ${settings.skipDelay} 毫秒再执行",
                value = delayValue,
                valueRange = 0f..3000f,
                steps = 29,
                onValueChange = { delayValue = it },
                onValueChangeFinished = {
                    viewModel.setSkipDelay(delayValue.toLong())
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // About
            SettingsSectionTitle("关于")

            val versionName = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
            } catch (e: Exception) { "unknown" }

            SettingsInfoItem(
                icon = Icons.Outlined.Info,
                title = "版本",
                subtitle = versionName
            )

            SettingsInfoItem(
                icon = Icons.Outlined.Description,
                title = "功能说明",
                subtitle = "通过无障碍服务检测并自动跳过广告",
                onClick = { /* Could open a help dialog */ }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Danger Zone
            SettingsSectionTitle("危险区域")

            SettingsActionItem(
                icon = Icons.Outlined.RestartAlt,
                title = "重置统计",
                subtitle = "清除所有跳过统计数据",
                textColor = MaterialTheme.colorScheme.error,
                onClick = { showResetStatsDialog = true }
            )

            SettingsActionItem(
                icon = Icons.Outlined.DeleteForever,
                title = "清除所有规则",
                subtitle = "删除所有自定义规则（不可恢复）",
                textColor = MaterialTheme.colorScheme.error,
                onClick = { showDeleteAllDialog = true }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Reset stats dialog
    if (showResetStatsDialog) {
        AlertDialog(
            onDismissRequest = { showResetStatsDialog = false },
            title = { Text("重置统计") },
            text = { Text("确定要重置所有统计数据吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetStats()
                        showResetStatsDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("重置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetStatsDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Delete all rules dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("清除所有规则") },
            text = { Text("确定要删除所有规则吗？此操作不可恢复！") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAllRules()
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("全部删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}

@Composable
private fun SettingsSliderItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(start = 40.dp)
        )
    }
}

@Composable
private fun SettingsInfoItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
}

@Composable
private fun SettingsActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(title, color = textColor)
        },
        supportingContent = {
            Text(subtitle, color = textColor.copy(alpha = 0.7f))
        },
        leadingContent = {
            Icon(icon, null, tint = textColor)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}