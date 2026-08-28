package com.example.homehealth.ui.screens.memberdetail

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.example.homehealth.ui.components.MemberAvatar
import com.example.homehealth.ui.components.MemberEditDialog
import com.example.homehealth.ui.components.TrendIndicator
import com.example.homehealth.ui.navigation.Routes
import com.example.homehealth.util.DateUtils
import com.example.homehealth.util.HealthTypes

/** 个人档案页：指标概览 + 最近记录 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailScreen(
    navController: NavHostController,
    viewModel: MemberDetailViewModel = hiltViewModel()
) {
    val member by viewModel.member.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val recentRecords by viewModel.recentRecords.collectAsStateWithLifecycle()
    val unreadAlerts by viewModel.unreadAlerts.collectAsStateWithLifecycle()
    var showEditDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(member?.name ?: "健康档案") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 编辑个人信息
                    IconButton(
                        onClick = { showEditDialog = true },
                        enabled = member != null
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑个人信息")
                    }
                    IconButton(onClick = { navController.navigate(Routes.ALERTS) }) {
                        Icon(Icons.Filled.Notifications, contentDescription = "预警中心")
                        if (unreadAlerts > 0) Badge { Text("$unreadAlerts") }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Routes.upload(viewModel.memberId)) },
                icon = { Icon(Icons.Filled.CloudUpload, contentDescription = null) },
                text = { Text("上传报告") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 成员信息头
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MemberAvatar(name = member?.name ?: "?", size = 56)
                        Column(modifier = Modifier.padding(start = 14.dp)) {
                            Text(
                                text = member?.name ?: "",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = buildString {
                                    append(member?.relationship ?: "")
                                    DateUtils.age(member?.dateOfBirth)?.let { append(" · $it 岁") }
                                    member?.gender?.let { g ->
                                        append(
                                            when (g) {
                                                "male" -> " · 男"
                                                "female" -> " · 女"
                                                else -> ""
                                            }
                                        )
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 未读预警提示
            if (unreadAlerts > 0) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(Routes.ALERTS) }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "有 $unreadAlerts 条未读健康预警，点击查看",
                                modifier = Modifier.padding(start = 10.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // 指标概览
            item {
                SectionHeader("健康指标")
            }
            if (metrics.isEmpty()) {
                item {
                    Text(
                        "暂无记录。点击「上传报告」拍照或选择体检报告照片，解析后自动生成指标。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(metrics, key = { it.type }) { metric ->
                    MetricCard(
                        metric = metric,
                        higherIsWorse = viewModel.higherIsWorse(metric.type),
                        onClick = {
                            navController.navigate(
                                Routes.record(viewModel.memberId, metric.type)
                            )
                        }
                    )
                }
            }

            // 最近记录
            if (recentRecords.isNotEmpty()) {
                item { SectionHeader("最近记录") }
                items(recentRecords, key = { it.id }) { record ->
                    RecentRecordRow(record)
                }
            }
        }
    }

    // 编辑个人信息对话框
    member?.let { current ->
        if (showEditDialog) {
            MemberEditDialog(
                member = current,
                onDismiss = { showEditDialog = false },
                onSave = { name, relationship, dob, gender, heightCm, weightKg ->
                    viewModel.updateMember(current, name, relationship, dob, gender, heightCm, weightKg)
                    showEditDialog = false
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun MetricCard(
    metric: MetricSummary,
    higherIsWorse: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = HealthTypes.label(metric.type),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = metric.latest.value,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = " ${metric.latest.unit}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    text = "${DateUtils.relative(metric.latest.recordDate)} · 共 ${metric.count} 条记录 · 参考 ${HealthTypes.range(metric.type)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            TrendIndicator(delta = metric.delta, higherIsWorse = higherIsWorse)
        }
    }
}

@Composable
private fun RecentRecordRow(record: HealthRecord) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = HealthTypes.label(record.type),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = DateUtils.formatDateTime(record.recordDate) +
                        if (record.sourceDocumentId != null) " · 报告解析" else " · 手动录入",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${record.value} ${record.unit}",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
