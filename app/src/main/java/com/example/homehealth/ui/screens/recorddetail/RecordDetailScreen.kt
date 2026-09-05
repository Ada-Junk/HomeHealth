package com.example.homehealth.ui.screens.recorddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.homehealth.data.local.entity.HealthRecord
import com.example.homehealth.ui.components.StatItem
import com.example.homehealth.ui.components.TrendLineChart
import com.example.homehealth.util.DateUtils
import com.example.homehealth.util.HealthTypes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 指标详情页：趋势图 + 统计 + 历史记录 + 手动添加 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    navController: NavHostController,
    viewModel: RecordDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val label = HealthTypes.label(viewModel.type)
    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<HealthRecord?>(null) }
    var deleteTarget by remember { mutableStateOf<HealthRecord?>(null) }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(label) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "手动添加记录")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 趋势图
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "历史趋势",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        if (state.chartPoints.isEmpty()) {
                            Text(
                                "暂无数据",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            TrendLineChart(points = state.chartPoints)
                        }
                    }
                }
            }

            // 统计
            if (state.records.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem("最新", formatNum(state.latest))
                            StatItem("平均", formatNum(state.average))
                            StatItem("最高", formatNum(state.highest))
                            StatItem("最低", formatNum(state.lowest))
                        }
                    }
                }
                item {
                    Text(
                        "参考范围：${HealthTypes.range(viewModel.type)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 记录列表
            item {
                Text(
                    "全部记录（${state.records.size}）",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(state.records, key = { it.id }) { record ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${record.value} ${record.unit}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = buildString {
                                    append(DateUtils.formatDateTime(record.recordDate))
                                    append(
                                        if (record.sourceDocumentId != null) " · 报告解析"
                                        else " · 手动录入"
                                    )
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { editTarget = record }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "编辑",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { deleteTarget = record }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        RecordEditDialog(
            type = viewModel.type,
            onDismiss = { showAddDialog = false },
            onConfirm = { primary, secondary, dateText, notes ->
                viewModel.addRecord(primary, secondary, dateText, notes)
                showAddDialog = false
            }
        )
    }

    // 编辑已有记录（预填现有值）
    editTarget?.let { record ->
        RecordEditDialog(
            type = viewModel.type,
            existing = record,
            onDismiss = { editTarget = null },
            onConfirm = { primary, secondary, dateText, notes ->
                viewModel.updateRecord(record, primary, secondary, dateText, notes)
                editTarget = null
            }
        )
    }

    deleteTarget?.let { record ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除记录") },
            text = { Text("确定删除 ${DateUtils.formatDate(record.recordDate)} 的记录（${record.value}）吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecord(record)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

private fun formatNum(v: Double?): String = v?.let { "%.1f".format(it) } ?: "—"

/** 添加 / 编辑记录对话框（编辑时预填现有值；血压需输入高压/低压） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordEditDialog(
    type: String,
    existing: HealthRecord? = null,
    onDismiss: () -> Unit,
    onConfirm: (primary: String, secondary: String?, dateText: String, notes: String?) -> Unit
) {
    // 编辑：预填现有值（血压拆分为高压/低压）
    var primary by remember(existing) {
        mutableStateOf(
            existing?.value?.split("/")?.firstOrNull()?.trim() ?: ""
        )
    }
    var secondary by remember(existing) {
        mutableStateOf(existing?.value?.split("/")?.getOrNull(1)?.trim() ?: "")
    }
    var dateText by remember(existing) {
        mutableStateOf(
            existing?.recordDate?.let { SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(it)) }
                ?: SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        )
    }
    var notes by remember(existing) { mutableStateOf(existing?.notes ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    val isBloodPressure = type == HealthTypes.BLOOD_PRESSURE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing == null) "添加${HealthTypes.label(type)}记录"
                else "编辑${HealthTypes.label(type)}记录"
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isBloodPressure) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = primary,
                            onValueChange = { primary = it; error = false },
                            label = { Text("收缩压(高压)") },
                            isError = error,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = secondary,
                            onValueChange = { secondary = it; error = false },
                            label = { Text("舒张压(低压)") },
                            isError = error,
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = primary,
                        onValueChange = { primary = it; error = false },
                        label = { Text("数值（${HealthTypes.unit(type)}）") },
                        isError = error,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text("测量日期：$dateText")
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("备注（可空）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) {
                    Text(
                        "请输入有效数值",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = primary.trim()
                val s = secondary.trim()
                val primaryOk = p.toDoubleOrNull() != null
                val secondaryOk = !isBloodPressure || s.toDoubleOrNull() != null
                if (p.isEmpty() || !primaryOk || !secondaryOk) {
                    error = true
                } else {
                    onConfirm(p, s.ifBlank { null }, dateText, notes)
                }
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        dateText = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(ms))
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
