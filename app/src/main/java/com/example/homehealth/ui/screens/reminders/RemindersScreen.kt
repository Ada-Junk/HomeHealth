package com.example.homehealth.ui.screens.reminders

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.homehealth.data.local.entity.FamilyMember
import com.example.homehealth.data.local.entity.MedicationReminder
import com.example.homehealth.ui.components.DropdownSelector
import com.example.homehealth.util.CalendarEventHelper
import com.example.homehealth.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 用药提醒页：列表 + 增删改 + 启停 + 写入本地日历 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    navController: NavHostController,
    viewModel: RemindersViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editTarget by remember { mutableStateOf<MedicationReminder?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember {
        mutableStateOf<com.example.homehealth.data.local.dao.ReminderWithMemberName?>(null)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 待写入日历的提醒（权限通过后继续执行）
    var pendingCalendarTarget by remember {
        mutableStateOf<com.example.homehealth.data.local.dao.ReminderWithMemberName?>(null)
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val target = pendingCalendarTarget
        pendingCalendarTarget = null
        if (grants.values.all { it }) {
            if (target != null) writeReminderToCalendar(
                context, viewModel, scope, snackbarHostState, target
            )
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("未授予日历权限，无法写入")
            }
        }
    }

    /** 请求权限（已有权限直接写入） */
    fun exportToCalendar(item: com.example.homehealth.data.local.dao.ReminderWithMemberName) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            writeReminderToCalendar(context, viewModel, scope, snackbarHostState, item)
        } else {
            pendingCalendarTarget = item
            calendarPermissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(title = { Text("用药提醒") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "添加提醒")
            }
        }
    ) { padding ->
        if (state.reminders.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "还没有用药提醒",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "点击右下角 + 添加药品与服药时间",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.reminders, key = { it.reminder.id }) { item ->
                    ReminderCard(
                        item = item,
                        onEdit = { editTarget = item.reminder },
                        onDelete = { deleteTarget = item },
                        onToggle = { viewModel.toggleActive(item.reminder) },
                        onExportCalendar = { exportToCalendar(item) }
                    )
                }
            }
        }
    }

    if (showAddDialog || editTarget != null) {
        ReminderEditDialog(
            existing = editTarget,
            members = state.members,
            onDismiss = {
                showAddDialog = false
                editTarget = null
            },
            onSave = { memberId, name, dosage, times ->
                viewModel.saveReminder(editTarget, memberId, name, dosage, times)
                showAddDialog = false
                editTarget = null
            }
        )
    }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除提醒") },
            text = {
                Text("确定删除「${item.reminder.medicationName}」的用药提醒吗？已写入日历的日程将一并删除。")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteReminder(item)
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
private fun ReminderCard(
    item: com.example.homehealth.data.local.dao.ReminderWithMemberName,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit,
    onExportCalendar: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.reminder.medicationName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${item.memberName} · ${item.reminder.dosage}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (item.reminder.active) "已启用" else "已停用",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = item.reminder.active,
                    onCheckedChange = { onToggle() }
                )
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item.reminder.dailyTimes().forEach { time ->
                    AssistChip(onClick = { }, label = { Text(time) })
                }
            }
            Text(
                text = "自 ${DateUtils.formatDate(item.reminder.startDate)} 起 · 每日提醒",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                // 写入系统日历（每日重复 + 提前提醒）
                OutlinedButton(onClick = onExportCalendar) {
                    Icon(
                        Icons.Filled.DateRange,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text("写入日历")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "编辑",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
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

/** 执行写入：先按签名清理旧日历事件（含旧版无 ID 的，避免重复），再写入新事件并持久化事件 ID */
private fun writeReminderToCalendar(
    context: android.content.Context,
    viewModel: RemindersViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
    item: com.example.homehealth.data.local.dao.ReminderWithMemberName
) {
    scope.launch {
        try {
            val isRewrite = item.reminder.calendarEventIdList().isNotEmpty()
            val eventIds = withContext(Dispatchers.IO) {
                // 重复写入时先删除旧日历事件（按记录 ID + 签名兜底，覆盖历史遗留事件）
                if (isRewrite || item.reminder.calendarEventIds != null) {
                    runCatching {
                        CalendarEventHelper.deleteMedicationEvents(
                            context = context,
                            medicationName = item.reminder.medicationName,
                            memberName = item.memberName,
                            storedEventIds = item.reminder.calendarEventIdList()
                        )
                    }
                }
                CalendarEventHelper.insertMedicationEvents(
                    context = context,
                    medicationName = item.reminder.medicationName,
                    dosage = item.reminder.dosage,
                    memberName = item.memberName,
                    times = item.reminder.dailyTimes()
                )
            }
            if (eventIds.isEmpty()) {
                snackbarHostState.showSnackbar("写入日历失败：服药时间格式无效")
                return@launch
            }
            // 持久化事件 ID，删除提醒时据此同步清理日历
            viewModel.updateCalendarEventIds(item.reminder, eventIds)
            val refreshed = if (isRewrite) "旧日程已更新" else "每日提醒"
            snackbarHostState.showSnackbar(
                "已写入日历：${item.reminder.medicationName}（${eventIds.size} 个时间点 · $refreshed）"
            )
        } catch (e: Exception) {
            snackbarHostState.showSnackbar("写入日历失败：${e.message}")
        }
    }
}

/** 添加/编辑用药提醒对话框 */
@Composable
private fun ReminderEditDialog(
    existing: MedicationReminder?,
    members: List<FamilyMember>,
    onDismiss: () -> Unit,
    onSave: (memberId: String, name: String, dosage: String, times: List<String>) -> Unit
) {
    var memberLabel by remember {
        mutableStateOf(
            members.firstOrNull { it.id == existing?.memberId }
                ?.let { "${it.name}（${it.relationship}）" }
                ?: members.firstOrNull()?.let { "${it.name}（${it.relationship}）" } ?: ""
        )
    }
    var name by remember { mutableStateOf(existing?.medicationName ?: "") }
    var dosage by remember { mutableStateOf(existing?.dosage ?: "") }
    var timesText by remember {
        mutableStateOf(existing?.dailyTimes()?.joinToString(", ") ?: "08:00, 20:00")
    }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "添加用药提醒" else "编辑用药提醒") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (members.isNotEmpty()) {
                    DropdownSelector(
                        options = members.map { "${it.name}（${it.relationship}）" },
                        selected = memberLabel,
                        label = "用药成员",
                        onSelect = { memberLabel = it }
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = false },
                    label = { Text("药品名称") },
                    isError = error,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("剂量（如 5mg × 1片）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = timesText,
                    onValueChange = { timesText = it; error = false },
                    label = { Text("每日时间（逗号分隔）") },
                    supportingText = { Text("格式：HH:mm，如 08:00, 20:00") },
                    isError = error,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) {
                    Text(
                        "请填写药品名称，时间格式为 HH:mm（逗号分隔）",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val member = members.firstOrNull {
                    "${it.name}（${it.relationship}）" == memberLabel
                }
                val times = timesText.split("，", ",").map { it.trim() }
                    .filter { it.matches(Regex("\\d{1,2}:\\d{2}")) }
                if (name.isBlank() || member == null || times.isEmpty()) {
                    error = true
                } else {
                    onSave(member.id, name.trim(), dosage.trim().ifBlank { "遵医嘱" }, times)
                }
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
