package com.dynamicconsent.ui.notice

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dynamicconsent.data.model.Notice
import com.dynamicconsent.ui.common.ErrorRetry
import com.dynamicconsent.ui.theme.AppBackground
import com.dynamicconsent.ui.theme.TextPrimary
import com.dynamicconsent.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val ChangedBadge = Color(0xFFEF4444)
private val UnchangedBadge = Color(0xFF9CA3AF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoticeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("공지사항", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        uiState.error?.let { message ->
            ErrorRetry(
                message = message,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(innerPadding),
            )
            return@Scaffold
        }

        if (uiState.notices.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "아직 확인된 약관이 없습니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(onClick = viewModel::retry) { Text("새로고침") }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { NoticeGuide() }
            items(uiState.notices, key = { "${it.companyId}-${it.checkedAtMillis}" }) { notice ->
                NoticeRow(notice)
            }
        }
    }
}

/**
 * 목록의 시각이 '변경 시각'이 아니라 '확인 시각'임을 먼저 알린다.
 * 이 구분이 없으면 매일 갱신되는 시각을 보고 약관이 매일 바뀐 것으로 오해한다.
 */
@Composable
private fun NoticeGuide() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppBackground, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(
            text = "약관을 마지막으로 확인한 기록입니다.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "매일 확인하며, 내용이 바뀌지 않아도 확인 시각은 갱신됩니다. 실제 변경 여부는 '변경됨' 표시로 확인하세요.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun NoticeRow(notice: Notice) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppBackground, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = notice.companyName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "확인 ${formatCheckedAt(notice.checkedAtMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        ChangeBadge(isChanged = notice.isChanged)
    }
}

@Composable
private fun ChangeBadge(isChanged: Boolean) {
    val color = if (isChanged) ChangedBadge else UnchangedBadge
    Text(
        text = if (isChanged) "변경됨" else "변경 없음",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = Modifier
            .background(color, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/**
 * 확인 시각을 KST로 고정해 표시한다.
 *
 * 기기 타임존으로 보여주면 순간 자체는 맞아도 서버가 말한 "새벽 3시 확인"이 다른 시각으로 보인다
 * (해외 로밍·에뮬레이터 등). 국내 서비스이고 서버 기준도 KST라 표시 기준을 서버에 맞춘다.
 */
private fun formatCheckedAt(millis: Long): String =
    SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA)
        .apply { timeZone = TimeZone.getTimeZone("Asia/Seoul") }
        .format(Date(millis))
