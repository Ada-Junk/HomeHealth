package com.example.homehealth.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.homehealth.data.local.entity.FamilyMember
import com.example.homehealth.data.remote.LlmProviders
import com.example.homehealth.ui.components.DropdownSelector
import com.example.homehealth.ui.components.MemberEditDialog
import com.example.homehealth.util.DateUtils
import com.example.homehealth.util.FileUtils

/** 设置页：成员管理 / 解析服务 / 数据导出 / 健康检查 / 关于 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val members by viewModel.members.collectAsStateWithLifecycle()
    val parseProvider by viewModel.parseProvider.collectAsStateWithLifecycle()
    val parseApiKey by viewModel.parseApiKey.collectAsStateWithLifecycle()
    val parseModel by viewModel.parseModel.collectAsStateWithLifecycle()
    val parseBaseUrl by viewModel.parseBaseUrl.collectAsStateWithLifecycle()
    val qaProvider by viewModel.qaProvider.collectAsStateWithLifecycle()
    val qaApiKey by viewModel.qaApiKey.collectAsStateWithLifecycle()
    val qaModel by viewModel.qaModel.collectAsStateWithLifecycle()
    val qaBaseUrl by viewModel.qaBaseUrl.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddMember by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<FamilyMember?>(null) }
    var deleteTarget by remember { mutableStateOf<FamilyMember?>(null) }

    // 事件处理
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ExportReady -> {
                    FileUtils.shareFile(
                        context = context,
                        file = event.file,
                        mime = "application/json",
                        title = "导出健康数据"
                    )
                }
                is SettingsEvent.CheckEnqueued -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = { androidx.compose.material3.TopAppBar(title = { Text("设置") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- 家庭成员管理 ----
            item {
                SectionTitle("家庭成员管理")
            }
            items(members, key = { it.id }) { member ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(member.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = buildString {
                                    append(member.relationship)
                                    DateUtils.age(member.dateOfBirth)?.let { append(" · $it 岁") }
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { editTarget = member }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "编辑",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { deleteTarget = member }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { showAddMember = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("添加家庭成员")
                }
            }

            // ---- 报告解析服务 ----
            item {
                SectionTitle("报告解析服务")
            }
            item {
                ProviderSettingsCard(
                    subtitle = "上传体检报告/化验单后，由视觉大模型识别并提取健康指标（支持血常规、肝肾功能、维生素等全类别）",
                    provider = parseProvider,
                    apiKey = parseApiKey,
                    model = parseModel,
                    baseUrl = parseBaseUrl,
                    vision = true,
                    onProviderChange = viewModel::setParseProvider,
                    onApiKeyChange = viewModel::setParseApiKey,
                    onModelChange = viewModel::setParseModel,
                    onBaseUrlChange = viewModel::setParseBaseUrl
                )
            }

            // ---- 健康问答服务 ----
            item {
                SectionTitle("健康问答服务")
            }
            item {
                ProviderSettingsCard(
                    subtitle = "在问答页提问时，由文本大模型结合成员健康记录生成回答；失败或未配置时自动使用本地分析",
                    provider = qaProvider,
                    apiKey = qaApiKey,
                    model = qaModel,
                    baseUrl = qaBaseUrl,
                    vision = false,
                    onProviderChange = viewModel::setQaProvider,
                    onApiKeyChange = viewModel::setQaApiKey,
                    onModelChange = viewModel::setQaModel,
                    onBaseUrlChange = viewModel::setQaBaseUrl
                )
            }

            // ---- 数据 ----
            item {
                SectionTitle("数据")
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Button(onClick = { viewModel.exportData() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text("导出全部数据（JSON）")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "导出内容包括成员、健康记录、预警、用药提醒与问答历史，通过系统分享发送。",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---- 健康检查 ----
            item {
                SectionTitle("健康检查")
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedButton(onClick = { viewModel.runCheckNow() }, modifier = Modifier.fillMaxWidth()) {
                            Text("立即执行健康检查")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "健康检查包括：发送当日用药提醒通知、对全部成员执行异常检测。系统也会每日 8:00 自动执行。",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---- 关于 ----
            item {
                SectionTitle("关于")
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("家庭健康管家 v1.0.0", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "隐私说明：所有健康数据仅保存在本机应用私有目录，不上传云端（除非您主动启用远程解析服务）。删除应用将同时删除全部数据。",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showAddMember) {
        MemberEditDialog(
            onDismiss = { showAddMember = false },
            onSave = { name, relationship, dob, gender, heightCm, weightKg ->
                viewModel.addMember(name, relationship, dob, gender, heightCm, weightKg)
                showAddMember = false
            }
        )
    }

    editTarget?.let { member ->
        MemberEditDialog(
            member = member,
            onDismiss = { editTarget = null },
            onSave = { name, relationship, dob, gender, heightCm, weightKg ->
                viewModel.updateMember(member, name, relationship, dob, gender, heightCm, weightKg)
                editTarget = null
            }
        )
    }

    deleteTarget?.let { member ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除成员") },
            text = { Text("删除「${member.name}」将同时删除其全部健康记录、预警、提醒与问答历史，且无法恢复。确定删除吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMember(member)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

/** 服务模式单选项 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeOptionCard(
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.padding(4.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    desc,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 服务供应商配置卡片：本地模式 / LLM 供应商直连 / 自建后端。
 * vision=true 为报告解析（视觉模型，不显示无视觉能力的供应商）；
 * vision=false 为健康问答（文本模型）。
 */
@Composable
private fun ProviderSettingsCard(
    subtitle: String,
    provider: String,
    apiKey: String,
    model: String,
    baseUrl: String,
    vision: Boolean,
    onProviderChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(subtitle, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))

            // 本地模式
            ModeOptionCard(
                title = "本地模式",
                desc = if (vision) "不联网，无法解析报告；可手动录入指标" else "不联网，使用本地规则分析已保存的记录",
                selected = provider == LlmProviders.LOCAL,
                onClick = { onProviderChange(LlmProviders.LOCAL) }
            )

            // LLM 供应商（解析场景跳过无视觉模型的供应商）
            LlmProviders.ALL.forEach { p ->
                if (vision && p.visionModels.isEmpty()) return@forEach
                Spacer(Modifier.height(8.dp))
                val models = if (vision) p.visionModels else p.chatModels
                ModeOptionCard(
                    title = p.name,
                    desc = listOfNotNull(p.note, p.keyHint).joinToString("；"),
                    selected = provider == p.id,
                    onClick = { onProviderChange(p.id) }
                )
                if (provider == p.id) {
                    Spacer(Modifier.height(10.dp))
                    ApiKeyField(apiKey = apiKey, onApiKeyChange = onApiKeyChange)

                    // 模型选择：自定义供应商手输，其余下拉选预设
                    if (p.id == LlmProviders.CUSTOM) {
                        Spacer(Modifier.height(8.dp))
                        var modelInput by remember(model) { mutableStateOf(model) }
                        OutlinedTextField(
                            value = modelInput,
                            onValueChange = {
                                modelInput = it
                                onModelChange(it)
                            },
                            label = { Text(if (vision) "视觉模型名称" else "文本模型名称") },
                            supportingText = {
                                Text(
                                    if (vision) "需支持图片输入的模型，如 qwen2.5-vl:7b、llava:13b"
                                    else "纯文本对话模型即可，如 qwen2.5:7b、deepseek-v3"
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        var urlInput by remember(baseUrl) { mutableStateOf(baseUrl) }
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = {
                                urlInput = it
                                onBaseUrlChange(it)
                            },
                            label = { Text("服务地址（OpenAI 兼容）") },
                            supportingText = { Text("以 /v1/ 结尾，如 http://192.168.1.10:11434/v1/") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (models.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        val display = model.ifBlank { "默认（${models.first()}）" }
                        DropdownSelector(
                            options = models,
                            selected = display,
                            label = if (vision) "视觉模型（用于报告解析）" else "文本模型（用于健康问答）",
                            onSelect = { onModelChange(it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 自建后端
            Spacer(Modifier.height(8.dp))
            ModeOptionCard(
                title = "自建后端服务",
                desc = "通过您部署的后端服务器中转调用 OCR/LLM，适合发布到应用商店（API Key 不进客户端）",
                selected = provider == LlmProviders.BACKEND,
                onClick = { onProviderChange(LlmProviders.BACKEND) }
            )
            if (provider == LlmProviders.BACKEND) {
                Spacer(Modifier.height(10.dp))
                var urlInput by remember(baseUrl) { mutableStateOf(baseUrl) }
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = {
                        urlInput = it
                        onBaseUrlChange(it)
                    },
                    label = { Text("后端地址") },
                    supportingText = { Text("修改后重启应用生效（默认 http://10.0.2.2:8000/ 指向模拟器宿主机）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** API Key 输入（密码遮罩 + 显示切换） */
@Composable
private fun ApiKeyField(apiKey: String, onApiKeyChange: (String) -> Unit) {
    var key by remember(apiKey) { mutableStateOf(apiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = key,
        onValueChange = {
            key = it
            onApiKeyChange(it)
        },
        label = { Text("API Key") },
        singleLine = true,
        visualTransformation = if (keyVisible) {
            androidx.compose.ui.text.input.VisualTransformation.None
        } else {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        },
        trailingIcon = {
            TextButton(onClick = { keyVisible = !keyVisible }) {
                Text(if (keyVisible) "隐藏" else "显示")
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}
