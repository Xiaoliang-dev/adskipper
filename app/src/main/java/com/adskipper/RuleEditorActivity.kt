package com.adskipper

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.adskipper.data.RuleEntity
import com.adskipper.ui.theme.AdSkipperTheme
import com.adskipper.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class RuleEditorActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var ruleId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ruleId = intent.getLongExtra("rule_id", 0)

        setContent {
            AdSkipperTheme {
                RuleEditorScreen(
                    viewModel = viewModel,
                    ruleId = ruleId,
                    onNavigateBack = { finish() }
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context, ruleId: Long = 0): Intent {
            return Intent(context, RuleEditorActivity::class.java).apply {
                putExtra("rule_id", ruleId)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorScreen(
    viewModel: MainViewModel,
    ruleId: Long,
    onNavigateBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isEditing = ruleId > 0

    // Form states
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("0") }
    var enabled by remember { mutableStateOf(true) }

    // Condition states
    var useText by remember { mutableStateOf(false) }
    var targetText by remember { mutableStateOf("") }
    var textMatchType by remember { mutableStateOf("contains") }

    var useId by remember { mutableStateOf(false) }
    var targetId by remember { mutableStateOf("") }
    var idMatchType by remember { mutableStateOf("contains") }

    var useClassName by remember { mutableStateOf(false) }
    var targetClassName by remember { mutableStateOf("") }
    var classMatchType by remember { mutableStateOf("contains") }

    var useContentDesc by remember { mutableStateOf(false) }
    var targetContentDesc by remember { mutableStateOf("") }
    var contentDescMatchType by remember { mutableStateOf("contains") }

    // Action states
    var actionType by remember { mutableStateOf("click") }
    var customClickX by remember { mutableStateOf("") }
    var customClickY by remember { mutableStateOf("") }
    var delayMs by remember { mutableStateOf("0") }
    var clickParent by remember { mutableStateOf(false) }

    var showAppPicker by remember { mutableStateOf(false) }

    // Load existing rule
    LaunchedEffect(ruleId) {
        if (isEditing) {
            try {
                val rule = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.adskipper.data.RuleManager.getInstance(context).getRuleById(ruleId)
                }
                rule?.let {
                    name = it.name
                    description = it.description
                    packageName = it.packageName
                    priority = it.priority.toString()
                    enabled = it.enabled
                    useText = it.useText
                    targetText = it.targetText
                    textMatchType = it.textMatchType
                    useId = it.useId
                    targetId = it.targetId
                    idMatchType = it.idMatchType
                    useClassName = it.useClassName
                    targetClassName = it.targetClassName
                    classMatchType = it.classMatchType
                    useContentDesc = it.useContentDesc
                    targetContentDesc = it.targetContentDesc
                    contentDescMatchType = it.contentDescMatchType
                    actionType = it.actionType
                    customClickX = if (it.customClickX >= 0) it.customClickX.toString() else ""
                    customClickY = if (it.customClickY >= 0) it.customClickY.toString() else ""
                    delayMs = it.delayMs.toString()
                    clickParent = it.clickParent
                }
            } catch (e: Exception) {
                android.util.Log.e("RuleEditor", "Error loading rule", e)
            }
        }
    }

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "编辑规则" else "添加规则") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (validateAndSave(
                                    viewModel, ruleId, name, description, packageName,
                                    priority, enabled, useText, targetText, textMatchType,
                                    useId, targetId, idMatchType, useClassName, targetClassName,
                                    classMatchType, useContentDesc, targetContentDesc,
                                    contentDescMatchType, actionType, customClickX, customClickY,
                                    delayMs, clickParent, onNavigateBack
                                )
                            ) {
                                Toast.makeText(context, "规则已保存", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("保存")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Basic Info Section
            SectionTitle("基本信息")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("规则名称 *") },
                placeholder = { Text("例如：跳过开屏广告") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("规则描述") },
                placeholder = { Text("可选，用于说明规则的用途") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Package selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("目标应用包名 *") },
                    placeholder = { Text("com.example.app") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { showAppPicker = true },
                    modifier = Modifier.height(56.dp)
                ) {
                    Icon(Icons.Default.Apps, contentDescription = "选择应用")
                }
            }

            // Priority
            OutlinedTextField(
                value = priority,
                onValueChange = { priority = it.filter { c -> c.isDigit() } },
                label = { Text("优先级") },
                placeholder = { Text("数字越大优先级越高") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Enabled switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("启用规则", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Conditions Section
            SectionTitle("触发条件 (至少选择一个)")

            // Text condition
            ConditionCard(
                title = "文本匹配",
                icon = Icons.Default.TextFields,
                enabled = useText,
                onEnabledChange = { useText = it }
            ) {
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = { Text("目标文本") },
                    placeholder = { Text("例如：跳过、关闭广告") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                MatchTypeSelector(
                    selected = textMatchType,
                    onSelected = { textMatchType = it }
                )
            }

            // ID condition
            ConditionCard(
                title = "ID 匹配",
                icon = Icons.Default.Fingerprint,
                enabled = useId,
                onEnabledChange = { useId = it }
            ) {
                OutlinedTextField(
                    value = targetId,
                    onValueChange = { targetId = it },
                    label = { Text("资源 ID") },
                    placeholder = { Text("例如：id/close_btn") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                MatchTypeSelector(
                    selected = idMatchType,
                    onSelected = { idMatchType = it }
                )
            }

            // Class name condition
            ConditionCard(
                title = "类名匹配",
                icon = Icons.Default.Code,
                enabled = useClassName,
                onEnabledChange = { useClassName = it }
            ) {
                OutlinedTextField(
                    value = targetClassName,
                    onValueChange = { targetClassName = it },
                    label = { Text("类名") },
                    placeholder = { Text("例如：android.widget.Button") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                MatchTypeSelector(
                    selected = classMatchType,
                    onSelected = { classMatchType = it }
                )
            }

            // Content description condition
            ConditionCard(
                title = "内容描述匹配",
                icon = Icons.Default.Description,
                enabled = useContentDesc,
                onEnabledChange = { useContentDesc = it }
            ) {
                OutlinedTextField(
                    value = targetContentDesc,
                    onValueChange = { targetContentDesc = it },
                    label = { Text("内容描述") },
                    placeholder = { Text("例如：关闭按钮") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                MatchTypeSelector(
                    selected = contentDescMatchType,
                    onSelected = { contentDescMatchType = it }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Action Section
            SectionTitle("执行动作")

            ActionTypeSelector(
                selected = actionType,
                onSelected = { actionType = it }
            )

            if (actionType == "custom_click") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customClickX,
                        onValueChange = { customClickX = it.filter { c -> c.isDigit() } },
                        label = { Text("X 坐标") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customClickY,
                        onValueChange = { customClickY = it.filter { c -> c.isDigit() } },
                        label = { Text("Y 坐标") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            OutlinedTextField(
                value = delayMs,
                onValueChange = { delayMs = it.filter { c -> c.isDigit() } },
                label = { Text("延迟 (毫秒)") },
                placeholder = { Text("检测到后等待多少毫秒执行") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("点击父元素", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = clickParent, onCheckedChange = { clickParent = it })
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // App Picker Dialog
    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onAppSelected = { packageName = it }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ConditionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (enabled) {
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        } else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            AnimatedVisibility(visible = enabled) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchTypeSelector(
    selected: String,
    onSelected: (String) -> Unit
) {
    val options = listOf(
        "contains" to "包含",
        "exact" to "完全匹配",
        "starts_with" to "开头匹配",
        "ends_with" to "结尾匹配",
        "regex" to "正则表达式"
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelected(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionTypeSelector(
    selected: String,
    onSelected: (String) -> Unit
) {
    val options = listOf(
        "click" to "点击",
        "click_parent" to "点击父元素",
        "swipe_left" to "左滑",
        "swipe_right" to "右滑",
        "back" to "返回键",
        "custom_click" to "自定义点击"
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelected(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerDialog(
    onDismiss: () -> Unit,
    onAppSelected: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf(listOf<Pair<String, String>>()) }

    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(0)
            .filter { it.packageName != context.packageName }
            .map { appInfo ->
                val label = pm.getApplicationLabel(appInfo).toString()
                val pkg = appInfo.packageName
                label to pkg
            }
            .sortedBy { it.first.lowercase() }
        apps = installedApps
    }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter {
            it.first.contains(searchQuery, ignoreCase = true) ||
            it.second.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择应用") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索应用...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(filteredApps.size) { index ->
                        val (label, pkg) = filteredApps[index]
                        ListItem(
                            headlineContent = { Text(label) },
                            supportingContent = { Text(pkg, style = MaterialTheme.typography.bodySmall) },
                            leadingContent = {
                                Icon(Icons.Default.Android, null, tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier.clickable {
                                onAppSelected(pkg)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun validateAndSave(
    viewModel: MainViewModel,
    ruleId: Long,
    name: String,
    description: String,
    packageName: String,
    priority: String,
    enabled: Boolean,
    useText: Boolean,
    targetText: String,
    textMatchType: String,
    useId: Boolean,
    targetId: String,
    idMatchType: String,
    useClassName: Boolean,
    targetClassName: String,
    classMatchType: String,
    useContentDesc: Boolean,
    targetContentDesc: String,
    contentDescMatchType: String,
    actionType: String,
    customClickX: String,
    customClickY: String,
    delayMs: String,
    clickParent: Boolean,
    onNavigateBack: () -> Unit
): Boolean {
    if (name.isBlank()) {
        // Show error
        return false
    }
    if (packageName.isBlank()) {
        return false
    }
    if (!useText && !useId && !useClassName && !useContentDesc) {
        return false
    }

    val rule = RuleEntity(
        id = ruleId,
        name = name.trim(),
        description = description.trim(),
        packageName = packageName.trim(),
        enabled = enabled,
        priority = priority.toIntOrNull() ?: 0,
        useText = useText,
        targetText = if (useText) targetText.trim() else "",
        textMatchType = textMatchType,
        useId = useId,
        targetId = if (useId) targetId.trim() else "",
        idMatchType = idMatchType,
        useClassName = useClassName,
        targetClassName = if (useClassName) targetClassName.trim() else "",
        classMatchType = classMatchType,
        useContentDesc = useContentDesc,
        targetContentDesc = if (useContentDesc) targetContentDesc.trim() else "",
        contentDescMatchType = contentDescMatchType,
        actionType = actionType,
        customClickX = if (actionType == "custom_click") customClickX.toIntOrNull() ?: -1 else -1,
        customClickY = if (actionType == "custom_click") customClickY.toIntOrNull() ?: -1 else -1,
        delayMs = delayMs.toLongOrNull() ?: 0L,
        clickParent = clickParent
    )

    if (ruleId > 0) {
        viewModel.updateRule(rule)
    } else {
        viewModel.addRule(rule)
    }
    onNavigateBack()
    return true
}