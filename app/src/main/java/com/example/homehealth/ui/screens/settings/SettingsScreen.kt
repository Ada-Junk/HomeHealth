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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.homehealth.R
import com.example.homehealth.data.SettingsPrefs
import com.example.homehealth.data.local.entity.FamilyMember
import com.example.homehealth.data.remote.LlmProviders
import com.example.homehealth.ui.components.DropdownSelector
import com.example.homehealth.ui.components.MemberEditDialog
import com.example.homehealth.ui.components.relationshipLabel
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
    val qaProvider by viewModel.qaProvider.collectAsStateWithLifecycle()
    val qaApiKey by viewModel.qaApiKey.collectAsStateWithLifecycle()
    val qaModel by viewModel.qaModel.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val languageMode by viewModel.languageMode.collectAsStateWithLifecycle()
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
                        title = context.getString(R.string.settings_export_share_title)
                    )
                }
                is SettingsEvent.CheckEnqueued -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = { androidx.compose.material3.TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
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
                SectionTitle(stringResource(R.string.settings_member_section))
            }
            items(members, key = { it.id }) { member ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        com.example.homehealth.ui.components.MemberAvatar(
                            name = member.name,
                            avatarUrl = member.avatarUrl,
                            size = 40
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            Text(member.name, style = MaterialTheme.typography.bodyLarge)
                            val ageText = DateUtils.age(member.dateOfBirth)
                                ?.let { stringResource(R.string.age_suffix, it) }
                            Text(
                                text = listOfNotNull(
                                    relationshipLabel(member.relationship),
                                    ageText
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { editTarget = member }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.common_edit),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { deleteTarget = member }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.common_delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { showAddMember = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text(stringResource(R.string.settings_add_member))
                }
            }

            // ---- 外观 ----
            item {
                SectionTitle(stringResource(R.string.settings_theme_section))
            }
            item {
                ModeOptionCard(
                    title = stringResource(R.string.settings_theme_system),
                    desc = stringResource(R.string.settings_theme_system_desc),
                    selected = themeMode == SettingsPrefs.THEME_SYSTEM,
                    onClick = { viewModel.setThemeMode(SettingsPrefs.THEME_SYSTEM) }
                )
            }
            item {
                ModeOptionCard(
                    title = stringResource(R.string.settings_theme_light),
                    desc = stringResource(R.string.settings_theme_light_desc),
                    selected = themeMode == SettingsPrefs.THEME_LIGHT,
                    onClick = { viewModel.setThemeMode(SettingsPrefs.THEME_LIGHT) }
                )
            }
            item {
                ModeOptionCard(
                    title = stringResource(R.string.settings_theme_dark),
                    desc = stringResource(R.string.settings_theme_dark_desc),
                    selected = themeMode == SettingsPrefs.THEME_DARK,
                    onClick = { viewModel.setThemeMode(SettingsPrefs.THEME_DARK) }
                )
            }

            // ---- 语言 ----
            item {
                SectionTitle(stringResource(R.string.settings_language_section))
            }
            item {
                ModeOptionCard(
                    title = stringResource(R.string.settings_language_system),
                    desc = stringResource(R.string.settings_language_system_desc),
                    selected = languageMode == SettingsPrefs.LANGUAGE_SYSTEM,
                    onClick = { viewModel.setLanguageMode(SettingsPrefs.LANGUAGE_SYSTEM) }
                )
            }
            item {
                ModeOptionCard(
                    title = stringResource(R.string.settings_language_zh),
                    desc = stringResource(R.string.settings_language_zh_desc),
                    selected = languageMode == SettingsPrefs.LANGUAGE_ZH,
                    onClick = { viewModel.setLanguageMode(SettingsPrefs.LANGUAGE_ZH) }
                )
            }
            item {
                ModeOptionCard(
                    title = stringResource(R.string.settings_language_en),
                    desc = stringResource(R.string.settings_language_en_desc),
                    selected = languageMode == SettingsPrefs.LANGUAGE_EN,
                    onClick = { viewModel.setLanguageMode(SettingsPrefs.LANGUAGE_EN) }
                )
            }

            // ---- 报告解析服务 ----
            item {
                SectionTitle(stringResource(R.string.settings_parse_section))
            }
            item {
                ProviderSettingsCard(
                    subtitle = stringResource(R.string.settings_parse_desc),
                    provider = parseProvider,
                    apiKey = parseApiKey,
                    model = parseModel,
                    vision = true,
                    onProviderChange = viewModel::setParseProvider,
                    onApiKeyChange = viewModel::setParseApiKey,
                    onModelChange = viewModel::setParseModel
                )
            }

            // ---- 健康问答服务 ----
            item {
                SectionTitle(stringResource(R.string.settings_qa_section))
            }
            item {
                ProviderSettingsCard(
                    subtitle = stringResource(R.string.settings_qa_desc),
                    provider = qaProvider,
                    apiKey = qaApiKey,
                    model = qaModel,
                    vision = false,
                    onProviderChange = viewModel::setQaProvider,
                    onApiKeyChange = viewModel::setQaApiKey,
                    onModelChange = viewModel::setQaModel
                )
            }

            // ---- 数据 ----
            item {
                SectionTitle(stringResource(R.string.settings_data_section))
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Button(onClick = { viewModel.exportData() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text(stringResource(R.string.settings_export))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.settings_export_desc),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---- 健康检查 ----
            item {
                SectionTitle(stringResource(R.string.settings_check_section))
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedButton(onClick = { viewModel.runCheckNow() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.settings_check_now))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.settings_check_desc),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---- 关于 ----
            item {
                SectionTitle(stringResource(R.string.settings_about_section))
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.settings_app_version), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.settings_privacy),
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
            onSave = { name, relationship, dob, gender, heightCm, weightKg, avatarUrl ->
                viewModel.addMember(name, relationship, dob, gender, heightCm, weightKg, avatarUrl)
                showAddMember = false
            }
        )
    }

    editTarget?.let { member ->
        MemberEditDialog(
            member = member,
            onDismiss = { editTarget = null },
            onSave = { name, relationship, dob, gender, heightCm, weightKg, avatarUrl ->
                viewModel.updateMember(member, name, relationship, dob, gender, heightCm, weightKg, avatarUrl)
                editTarget = null
            }
        )
    }

    deleteTarget?.let { member ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.settings_delete_member_title)) },
            text = { Text(stringResource(R.string.settings_delete_member_confirm, member.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMember(member)
                    deleteTarget = null
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.common_cancel)) }
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
 * 服务供应商配置卡片：本地模式 / LLM 供应商直连。
 * vision=true 为报告解析（视觉模型，不显示无视觉能力的供应商）；
 * vision=false 为健康问答（文本模型）。
 */
@Composable
private fun ProviderSettingsCard(
    subtitle: String,
    provider: String,
    apiKey: String,
    model: String,
    vision: Boolean,
    onProviderChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(subtitle, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))

            // 本地模式
            ModeOptionCard(
                title = stringResource(R.string.provider_local),
                desc = stringResource(
                    if (vision) R.string.provider_local_vision_desc
                    else R.string.provider_local_qa_desc
                ),
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

                    // 模型下拉选择（供应商预设模型清单）
                    if (models.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        val display = model.ifBlank {
                            stringResource(R.string.settings_model_default, models.first())
                        }
                        DropdownSelector(
                            options = models,
                            selected = display,
                            label = stringResource(
                                if (vision) R.string.settings_vision_model_label
                                else R.string.settings_text_model_label
                            ),
                            onSelect = { onModelChange(it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
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
                Text(stringResource(if (keyVisible) R.string.settings_key_hide else R.string.settings_key_show))
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}
