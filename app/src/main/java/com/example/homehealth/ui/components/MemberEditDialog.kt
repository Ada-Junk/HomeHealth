package com.example.homehealth.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.homehealth.data.local.entity.FamilyMember
import com.example.homehealth.util.DateUtils

/** 关系选项（按辈分排序） */
val RELATIONSHIPS = listOf(
    "本人", "配偶", "父亲", "母亲", "公公", "婆婆", "岳父", "岳母",
    "哥哥", "姐姐", "弟弟", "妹妹", "儿子", "女儿", "其他"
)
val GENDERS = listOf("男" to "male", "女" to "female", "其他" to "other")

/** 添加 / 编辑家庭成员对话框 */
@Composable
fun MemberEditDialog(
    member: FamilyMember? = null,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        relationship: String,
        dateOfBirth: String?,
        gender: String?,
        heightCm: Double?,
        weightKg: Double?
    ) -> Unit
) {
    var name by remember { mutableStateOf(member?.name ?: "") }
    var relationship by remember { mutableStateOf(member?.relationship ?: "本人") }
    var dob by remember { mutableStateOf(member?.dateOfBirth ?: "") }
    var genderLabel by remember { mutableStateOf(
        GENDERS.firstOrNull { it.second == member?.gender }?.first ?: "男"
    ) }
    var height by remember { mutableStateOf(member?.heightCm?.let { trimNum(it) } ?: "") }
    var weight by remember { mutableStateOf(member?.weightKg?.let { trimNum(it) } ?: "") }
    var nameError by remember { mutableStateOf(false) }
    var dobError by remember { mutableStateOf(false) }

    // 根据出生日期实时计算年龄（输入合法即显示）
    val ageText = remember(dob) {
        DateUtils.age(dob.trim())?.let { "$it 岁" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (member == null) "添加家庭成员" else "编辑成员信息") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("姓名") },
                    isError = nameError,
                    supportingText = { if (nameError) Text("请输入姓名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownSelector(
                    options = RELATIONSHIPS,
                    selected = relationship,
                    label = "与我的关系",
                    onSelect = { relationship = it }
                )
                DropdownSelector(
                    options = GENDERS.map { it.first },
                    selected = genderLabel,
                    label = "性别",
                    onSelect = { genderLabel = it }
                )
                OutlinedTextField(
                    value = dob,
                    onValueChange = { dob = it; dobError = false },
                    label = { Text("出生日期（yyyy-MM-dd，可空）") },
                    isError = dobError,
                    supportingText = {
                        when {
                            dobError -> Text("日期格式不正确")
                            ageText != null -> Text("年龄：$ageText（自动计算）")
                            else -> Text("")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = height,
                        onValueChange = { height = filterNumber(it) },
                        label = { Text("身高（cm，可空）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = filterNumber(it) },
                        label = { Text("体重（kg，可空）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmedName = name.trim()
                val trimmedDob = dob.trim()
                val validDob = trimmedDob.isEmpty() || DateUtils.parseDate(trimmedDob) != null
                if (trimmedName.isEmpty()) {
                    nameError = true
                } else if (!validDob) {
                    dobError = true
                } else {
                    onSave(
                        trimmedName,
                        relationship,
                        trimmedDob.ifBlank { null },
                        GENDERS.first { it.first == genderLabel }.second,
                        height.trim().toDoubleOrNull(),
                        weight.trim().toDoubleOrNull()
                    )
                }
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 只保留数字与一个小数点 */
private fun filterNumber(input: String): String {
    val cleaned = input.filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    return if (firstDot >= 0) {
        cleaned.substring(0, firstDot + 1) + cleaned.substring(firstDot + 1).replace(".", "")
    } else cleaned
}

/** Double 转字符串去掉多余小数位（175.0 → 175） */
private fun trimNum(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/** 下拉选择器 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownSelector(
    options: List<T>,
    selected: T,
    label: String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.toString()) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
