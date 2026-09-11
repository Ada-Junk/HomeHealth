package com.example.homehealth.ui.screens.qa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.homehealth.R
import com.example.homehealth.data.local.entity.QAHistory
import com.example.homehealth.ui.components.memberPickerLabel
import com.example.homehealth.util.DateUtils

/** 健康问答页：聊天式界面，基于成员健康数据回答 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QAScreen(
    navController: NavHostController,
    viewModel: QAViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // 新消息（含待回答问题）时自动滚动到底部
    LaunchedEffect(state.history.size, state.pendingQuestion) {
        val last = state.history.size + (if (state.pendingQuestion != null) 1 else 0)
        if (last > 0) {
            listState.animateScrollToItem(last - 1)
        }
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(title = { Text(stringResource(R.string.qa_title)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            // 成员选择 + 提示
            if (state.members.isNotEmpty()) {
                val memberOptions = state.members.map { it to memberPickerLabel(it.name, it.relationship) }
                com.example.homehealth.ui.components.DropdownSelector(
                    options = memberOptions.map { it.second },
                    selected = memberOptions
                        .firstOrNull { it.first.id == state.selectedMemberId }
                        ?.second ?: "",
                    label = stringResource(R.string.qa_member_label),
                    onSelect = { label ->
                        memberOptions.firstOrNull { it.second == label }
                            ?.let { viewModel.selectMember(it.first.id) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Text(
                    stringResource(R.string.qa_disclaimer),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // 消息列表
            Box(modifier = Modifier.weight(1f)) {
                if (state.history.isEmpty() && !state.loading) {
                    Text(
                        if (state.members.isEmpty()) stringResource(R.string.qa_empty_members)
                        else stringResource(R.string.qa_try_ask),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp)
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.history, key = { it.id }) { item ->
                        ChatBubble(item)
                    }
                    // 已发送待回答的问题：立即上屏，无需等待 LLM 返回
                    state.pendingQuestion?.let { q ->
                        item {
                            PendingQuestionBubble(q)
                        }
                    }
                    if (state.loading) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 4.dp, topEnd = 16.dp,
                                    bottomStart = 16.dp, bottomEnd = 16.dp
                                ),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.widthIn(max = 300.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        stringResource(R.string.qa_analyzing),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            state.error?.let { msg ->
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // 输入栏（紧凑：48dp 高 + 14sp 文字；imePadding 使其始终位于输入法之上）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = {
                        Text(stringResource(R.string.qa_input_hint), style = MaterialTheme.typography.bodyMedium)
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                )
                IconButton(
                    onClick = {
                        // 发起成功才清空输入；未发起（无成员/加载中）时保留文字
                        if (viewModel.ask(input)) {
                            input = ""
                            // 收起输入法，输入框随 imePadding 释放自动下沉到底部
                            keyboardController?.hide()
                        }
                    },
                    enabled = input.isNotBlank() && !state.loading && state.members.isNotEmpty()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.qa_send_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** 待回答的用户问题气泡（样式与历史问题气泡一致） */
@Composable
private fun PendingQuestionBubble(question: String) {
    Surface(
        shape = RoundedCornerShape(
            topStart = 16.dp, topEnd = 4.dp,
            bottomStart = 16.dp, bottomEnd = 16.dp
        ),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.widthIn(max = 300.dp)
    ) {
        Text(
            text = question,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun ChatBubble(item: QAHistory) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        // 用户问题
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 4.dp,
                bottomStart = 16.dp, bottomEnd = 16.dp
            ),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = item.question,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(12.dp)
            )
        }

        Spacer8()

        // 助手回答
        Surface(
            shape = RoundedCornerShape(
                topStart = 4.dp, topEnd = 16.dp,
                bottomStart = 16.dp, bottomEnd = 16.dp
            ),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .align(Alignment.Start)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // 思考过程（深度思考模型返回，默认收起，点击展开）
                if (!item.thinking.isNullOrBlank()) {
                    ThinkingBlock(thinking = item.thinking!!)
                }
                Text(
                    text = item.answer,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!item.sources.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.qa_sources, item.sources),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Text(
                    text = DateUtils.formatDateTime(item.timestamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** 思考过程块：默认收起，点击「查看思考过程」展开 */
@Composable
private fun ThinkingBlock(thinking: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stringResource(
                    if (expanded) R.string.qa_thinking_hide
                    else R.string.qa_thinking_show
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Text(
                text = thinking,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun Spacer8() {
    androidx.compose.foundation.layout.Spacer(
        modifier = Modifier.padding(top = 4.dp)
    )
}
