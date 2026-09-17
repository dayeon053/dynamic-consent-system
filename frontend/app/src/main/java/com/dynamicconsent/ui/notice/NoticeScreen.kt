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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dynamicconsent.data.model.Notice
import com.dynamicconsent.ui.common.ErrorRetry
import com.dynamicconsent.ui.common.keepWordsWhole
import com.dynamicconsent.ui.theme.TextPrimary
import com.dynamicconsent.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
                    text = keepWordsWhole("아직 변경된 약관이 없습니다."),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    // "확인된 약관이 없다"고 하면 서버가 놀고 있는 것처럼 읽힌다.
                    // 실제로는 매일 확인하고 있고, 바뀐 게 없어서 목록이 빈 것이다.
                    text = keepWordsWhole("매일 새벽 약관을 확인하고 있습니다. 바뀐 내용이 생기면 여기에 올라옵니다."),
                    style = MaterialTheme.typography.bodyMedium,
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
 * 이 목록이 '매일의 확인 기록'이 아니라 '바뀐 것만 모은 기록'임을 먼저 알린다.
 * 함께 적히는 시각이 '변경 시각'이 아니라 '확인 시각'이라는 것도 같이 짚는다.
 */
@Composable
private fun NoticeGuide() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Text(
            text = "약관이 바뀐 기록만 모았습니다.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = keepWordsWhole(
                "매일 새벽 전체 기업의 약관을 확인하고, 바뀐 내용이 있을 때만 여기에 올라옵니다. " +
                    "함께 적힌 시각은 그 변경을 확인한 시각입니다.",
            ),
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
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
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
            // 목록이 변경 건만 담으므로 줄마다 "변경됨" 배지를 붙이면 같은 말이 반복된다.
            // 홈 화면과 같은 방식으로 문장에 담는다. false는 정상 서버에서는 오지 않는다.
            Text(
                text = if (notice.isChanged) {
                    "개인정보 처리방침이 변경되었습니다"
                } else {
                    "변경 없이 확인되었습니다"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${formatCheckedAt(notice.checkedAtMillis)} 확인",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
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
