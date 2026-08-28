package com.example.homehealth.ui.screens.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.homehealth.ui.components.SeverityBadge
import com.example.homehealth.util.DateUtils
import kotlinx.coroutines.launch

/** 预警中心：按严重程度排序，支持未读过滤与立即检测 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    navController: NavHostController,
    viewModel: AlertsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("预警中心") },
                actions = {
                    IconButton(onClick = {
                        viewModel.runDetection { created ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (created > 0) "检测完成，新增 $created 条预警"
                                    else "检测完成，暂无新的异常"
                                )
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "立即检测")
                    }
                    IconButton(onClick = { viewModel.markAllRead() }) {
                        Icon(Icons.Filled.DoneAll, contentDescription = "全部已读")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (state.detecting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.filter == AlertFilter.ALL,
                    onClick = { viewModel.setFilter(AlertFilter.ALL) },
                    label = { Text("全部（${state.alerts.size}）") }
                )
                FilterChip(
                    selected = state.filter == AlertFilter.UNREAD,
                    onClick = { viewModel.setFilter(AlertFilter.UNREAD) },
                    label = { Text("未读（${state.unreadCount}）") }
                )
            }

            if (state.alerts.isEmpty()) {
                Text(
                    if (state.filter == AlertFilter.UNREAD) "没有未读预警"
                    else "暂无预警。上传报告或点击右上角「立即检测」分析健康数据。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.alerts, key = { it.alert.id }) { item ->
                        AlertCard(
                            item = item,
                            onClick = { if (!item.alert.isRead) viewModel.markRead(item.alert.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertCard(
    item: com.example.homehealth.data.local.dao.AlertWithMemberName,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeverityBadge(severity = item.alert.severity)
                Text(
                    text = item.alert.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                )
                if (!item.alert.isRead) {
                    androidx.compose.material3.Badge { }
                }
            }
            Text(
                text = item.alert.description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                text = "${item.memberName} · ${DateUtils.formatDateTime(item.alert.createdDate)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
