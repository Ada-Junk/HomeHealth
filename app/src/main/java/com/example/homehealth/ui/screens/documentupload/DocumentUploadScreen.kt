package com.example.homehealth.ui.screens.documentupload

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.example.homehealth.data.local.entity.MedicalDocument
import com.example.homehealth.ui.components.ParseStatusBadge
import com.example.homehealth.util.DateUtils
import com.example.homehealth.util.HealthTypes
import java.io.File

/** 文档上传页：拍照/相册选择 → 解析 → 编辑确认 → 入库 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentUploadScreen(
    navController: NavHostController,
    viewModel: DocumentUploadViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRawText by remember { mutableStateOf(false) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            viewModel.pendingCameraUri?.let { viewModel.onImageReady(it) }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.prepareCameraCapture(context)?.let { takePictureLauncher.launch(it) }
        } else {
            // 无相机权限时提示
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.onImageReady(it) }
    }

    // 保存完成后返回
    LaunchedEffect(state.phase) {
        when (state.phase) {
            UploadPhase.DONE -> {
                snackbarHostState.showSnackbar("记录已保存，已完成异常检测")
                viewModel.resetToIdle()
                navController.popBackStack()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("上传报告") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 成员选择
            if (state.members.size > 1) {
                item {
                    com.example.homehealth.ui.components.DropdownSelector(
                        options = state.members.map { "${it.name}（${it.relationship}）" },
                        selected = state.members
                            .firstOrNull { it.id == state.selectedMemberId }
                            ?.let { "${it.name}（${it.relationship}）" } ?: "",
                        label = "归档成员",
                        onSelect = { label ->
                            state.members.firstOrNull {
                                "${it.name}（${it.relationship}）" == label
                            }?.let { viewModel.selectMember(it.id) }
                        }
                    )
                }
            }

            // 图片预览
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (state.imagePath != null) {
                            AsyncImage(
                                model = File(state.imagePath!!),
                                contentDescription = "报告图片",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "拍摄或选择体检报告 / 化验单照片",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        viewModel.prepareCameraCapture(context)
                                            ?.let { takePictureLauncher.launch(it) }
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Filled.PhotoCamera,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Text("拍照")
                            }
                            OutlinedButton(
                                onClick = {
                                    pickImageLauncher.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Filled.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Text("相册")
                            }
                        }
                    }
                }
            }

            // 解析进度
            if (state.phase == UploadPhase.SAVING || state.phase == UploadPhase.PARSING ||
                state.phase == UploadPhase.CONFIRMING
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                when (state.phase) {
                                    UploadPhase.SAVING -> "正在保存图片…"
                                    UploadPhase.PARSING -> "正在解析报告（OCR + 结构化提取）…"
                                    else -> "正在保存记录…"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            // 错误提示
            if (state.phase == UploadPhase.ERROR && state.errorMessage != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            state.errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // 可编辑解析结果
            if (state.phase == UploadPhase.PARSED && state.editableRecords.isNotEmpty()) {
                item {
                    Text(
                        "解析结果（请核对后保存）",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(state.editableRecords, key = { it.id }) { record ->
                    EditableRecordCard(
                        record = record,
                        onChange = { newRecord ->
                            viewModel.updateEditableRecord(record.id, { newRecord })
                            newRecord
                        },
                        onRemove = { viewModel.removeEditableRecord(record.id) }
                    )
                }

                item {
                    Button(
                        onClick = { viewModel.confirmRecords() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("确认保存 ${state.editableRecords.size} 条记录")
                    }
                }
                if (state.rawText.isNotBlank()) {
                    item {
                        TextButton(onClick = { showRawText = !showRawText }) {
                            Text(if (showRawText) "收起 OCR 原始文本" else "查看 OCR 原始文本")
                        }
                    }
                    if (showRawText) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    state.rawText,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 解析历史
            if (state.documents.isNotEmpty()) {
                item {
                    Text(
                        "解析历史",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(state.documents, key = { it.id }) { document ->
                    DocumentHistoryRow(
                        document = document,
                        onRetry = { viewModel.retryParse(document) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EditableRecordCard(
    record: EditableRecord,
    onChange: (EditableRecord) -> EditableRecord,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.example.homehealth.ui.components.DropdownSelector(
                    options = HealthTypes.ALL.map { HealthTypes.label(it) },
                    selected = HealthTypes.label(record.type),
                    label = "指标",
                    onSelect = { label ->
                        val type = HealthTypes.ALL.first { HealthTypes.label(it) == label }
                        onChange(record.copy(type = type))
                    },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = record.value,
                    onValueChange = { onChange(record.copy(value = it)) },
                    label = { Text("数值") },
                    singleLine = true,
                    modifier = Modifier.weight(1.4f)
                )
                OutlinedTextField(
                    value = record.unit,
                    onValueChange = { onChange(record.copy(unit = it)) },
                    label = { Text("单位") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = record.date,
                onValueChange = { onChange(record.copy(date = it)) },
                label = { Text("日期（yyyy-MM-dd）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DocumentHistoryRow(
    document: MedicalDocument,
    onRetry: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    document.fileName,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    DateUtils.formatDateTime(document.uploadDate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ParseStatusBadge(status = document.parseStatus)
            when (document.parseStatus) {
                com.example.homehealth.data.local.entity.ParseStatus.FAILED ->
                    TextButton(onClick = onRetry) { Text("重试") }
                // 已完成的文档也允许重新解析（如记录被误删后恢复）
                com.example.homehealth.data.local.entity.ParseStatus.COMPLETED ->
                    TextButton(onClick = onRetry) { Text("重新解析") }
                else -> {}
            }
        }
    }
}
