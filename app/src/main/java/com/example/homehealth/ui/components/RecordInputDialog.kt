package com.example.homehealth.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.homehealth.data.local.entity.HealthRecord
import com.example.homehealth.util.HealthTypes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 添加 / 编辑健康记录对话框（共享组件）：
 * - [fixedType] 为空且非编辑时显示指标类型选择器（个人档案手动录入）；
 * - [fixedType] 非空时锁定指标类型（指标详情页）；
 * - 编辑（[existing] 非空）时预填现有值，血压自动拆分为高压/低压。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordInputDialog(
    fixedType: String? = null,
    existing: HealthRecord? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        type: String,
        primary: String,
        secondary: String?,
        dateText: String,
        notes: String?
    ) -> Unit
) {
    val isEdit = existing != null
    val typeLocked = fixedType != null || isEdit

    var selectedType by remember(existing) {
        mutableStateOf(existing?.type ?: fixedType ?: HealthTypes.BLOOD_PRESSURE)
    }
    // 编辑：预填现有值（血压拆分为高压/低压）
    var primary by remember(existing) {
        mutableStateOf(existing?.value?.split("/")?.firstOrNull()?.trim() ?: "")
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

    val isBloodPressure = selectedType == HealthTypes.BLOOD_PRESSURE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    isEdit -> "编辑${HealthTypes.label(selectedType)}记录"
                    typeLocked -> "添加${HealthTypes.label(selectedType)}记录"
                    else -> "添加健康指标"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 未锁定类型时可选指标（49 项标准指标体系）
                if (!typeLocked) {
                    DropdownSelector(
                        options = HealthTypes.ALL.map { HealthTypes.label(it) },
                        selected = HealthTypes.label(selectedType),
                        label = "指标类型",
                        onSelect = { label ->
                            selectedType = HealthTypes.ALL.first { HealthTypes.label(it) == label }
                            error = false
                        }
                    )
                }
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
                        label = { Text("数值（${HealthTypes.unit(selectedType)}）") },
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
                    onConfirm(selectedType, p, s.ifBlank { null }, dateText, notes)
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
