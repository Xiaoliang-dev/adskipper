package com.adskipper.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adskipper.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import java.net.HttpURLConnection
import java.net.URL

private const val GITHUB_API = "https://api.github.com/repos/Xiaoliang-dev/adskipper/releases/latest"

enum class UpdateState { Idle, Checking, Available, Error }

data class UpdateCheckResult(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String
)

private suspend fun checkForUpdate(): UpdateCheckResult? {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val json = conn.inputStream.bufferedReader().readText()
            val gson = Gson()
            val map = gson.fromJson(json, Map::class.java) as Map<String, Any>
            val tagName = map["tag_name"] as? String ?: return@withContext null
            val body = map["body"] as? String ?: ""
            val assets = map["assets"] as? List<Map<String, Any>> ?: emptyList()
            val downloadUrl = assets
                .firstOrNull { (it["name"] as? String)?.endsWith(".apk") == true }
                ?.let { it["browser_download_url"] as? String }
                ?: (map["html_url"] as? String ?: "")

            UpdateCheckResult(
                latestVersion = tagName.removePrefix("v"),
                downloadUrl = downloadUrl,
                releaseNotes = body.take(500)
            )
        } catch (e: Exception) {
            null
        }
    }
}

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
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var checkState by remember { mutableStateOf(UpdateState.Idle) }
    val scope = rememberCoroutineScope()

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
                onCheckedChange = { viewModel.setShowToast(it) }
            )

            SettingsSwitchItem(
                icon = Icons.Outlined.Vibration,
                title = "振动反馈",
                subtitle = "跳过广告时振动提示",
                checked = settings.vibrate,
                onCheckedChange = { viewModel.setVibrate(it) }
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
                onValueChangeFinished = { viewModel.setSkipDelay(delayValue.toLong()) }
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

            // Check for updates
            SettingsActionItem(
                icon = Icons.Outlined.Update,
                title = "检查更新",
                subtitle = when (checkState) {
                    UpdateState.Checking -> "正在检查..."
                    UpdateState.Error -> "检查失败，点击重试"
                    else -> "从 GitHub 检查最新版本"
                },
                subtitleColor = when (checkState) {
                    UpdateState.Error -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                onClick = {
                    scope.launch {
                        if (checkState == UpdateState.Checking) return@launch
                        checkState = UpdateState.Checking
                        val result = checkForUpdate()
                        if (result != null) {
                            val currentVer = versionName.takeWhile { it.isDigit() || it == '.' }
                            val cleanVersion = result.latestVersion.takeWhile { it.isDigit() || it == '.' }
                            if (cleanVersion > currentVer) {
                                updateInfo = result
                                checkState = UpdateState.Available
                                showUpdateDialog = true
                            } else {
                                checkState = UpdateState.Idle
                                android.widget.Toast.makeText(context, "已是最新版本", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            checkState = UpdateState.Error
                        }
                    }
                }
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

    // Update dialog
    if (showUpdateDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("发现新版本 ${updateInfo!!.latestVersion}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("新版本已发布，是否前往下载？")
                    if (updateInfo!!.releaseNotes.isNotBlank()) {
                        Text(
                            text = updateInfo!!.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUpdateDialog = false
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo!!.downloadUrl))
                        context.startActivity(intent)
                    }
                ) {
                    Text("前往下载")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("稍后再说")
                }
            }
        )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
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
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onClick != null) {
            Icon(
                Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null, tint = textColor, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Default.ChevronRight, null,
            tint = textColor.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}
