package com.dynamicconsent.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dynamicconsent.data.model.Notice
import com.dynamicconsent.data.model.RecentConsentChange
import com.dynamicconsent.ui.common.ErrorRetry
import com.dynamicconsent.ui.common.formatKstShortDate
import com.dynamicconsent.ui.theme.AppBackground
import com.dynamicconsent.ui.theme.BrandGreen
import com.dynamicconsent.ui.theme.RiskHigh
import com.dynamicconsent.ui.theme.TextPrimary
import com.dynamicconsent.ui.theme.TextSecondary

private val CardWhite = Color.White

/**
 * 홈 화면.
 *
 * 위험 감지 요약 → 카테고리 바로가기 → 최근 동의 변경 내역 → 공지사항 순으로,
 * 사용자가 "지금 확인할 게 있나"를 먼저 보고 각 화면으로 들어가게 구성한다.
 */
@Composable
fun HomeScreen(
    onRiskListClick: () -> Unit,
    onCategoryClick: (category: String) -> Unit,
    onNoticeClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    uiState.error?.let { message ->
        ErrorRetry(message = message, onRetry = viewModel::retry, modifier = modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        HomeHeader(onNoticeClick = onNoticeClick)

        Spacer(modifier = Modifier.height(16.dp))
        RiskSummaryCard(
            riskyCount = uiState.riskyOrganizationCount,
            onClick = onRiskListClick,
        )

        if (uiState.categories.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            CategoryShortcuts(categories = uiState.categories, onCategoryClick = onCategoryClick)
        }

        Spacer(modifier = Modifier.height(28.dp))
        SectionHeader(title = "최근 동의 변경 내역")
        Spacer(modifier = Modifier.height(12.dp))
        RecentChangesSection(changes = uiState.recentChanges)

        Spacer(modifier = Modifier.height(28.dp))
        SectionHeader(title = "공지사항", onMoreClick = onNoticeClick)
        Spacer(modifier = Modifier.height(12.dp))
        NoticesSection(notices = uiState.notices)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun HomeHeader(onNoticeClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 로그인 기능이 없어 데모 사용자 이름을 그대로 쓴다 (DEMO_USER_ID = 1).
        Text(
            text = "${DEMO_USER_NAME}님",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = BrandGreen,
        )
        IconButton(onClick = onNoticeClick) {
            Icon(Icons.Default.Notifications, contentDescription = "공지사항", tint = TextPrimary)
        }
    }
}

@Composable
private fun RiskSummaryCard(riskyCount: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardWhite, RoundedCornerShape(16.dp))
            .border(2.dp, RiskHigh, RoundedCornerShape(16.dp))
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (riskyCount > 0) {
                    Row {
                        Text(
                            text = "${riskyCount}개 기관",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = RiskHigh,
                        )
                        Text(
                            text = "에서",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "위험도가 감지되었어요",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                } else {
                    Text(
                        text = "감지된 위험이 없어요",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = RiskHigh,
                modifier = Modifier.size(56.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("해당 기관 확인하기", color = BrandGreen, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = BrandGreen)
        }
    }
}

/**
 * 카테고리 바로가기. 목업의 고정 4개(금융/SNS/쇼핑/교통) 대신
 * **실제 데이터에 존재하는 카테고리**로 만든다 — 없는 카테고리를 눌렀을 때 빈 목록이 뜨는 걸 막는다.
 */
@Composable
private fun CategoryShortcuts(categories: List<String>, onCategoryClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardWhite, RoundedCornerShape(16.dp))
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        categories.forEach { category ->
            Column(
                modifier = Modifier
                    .clickable { onCategoryClick(category) }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(AppBackground, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = category.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BrandGreen,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onMoreClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        onMoreClick?.let {
            Row(
                modifier = Modifier.clickable(onClick = it),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("더보기", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun RecentChangesSection(changes: List<RecentConsentChange>) {
    if (changes.isEmpty()) {
        EmptyCard("아직 변경한 동의가 없습니다.")
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        changes.forEach { change ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(CardWhite, RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Text(
                    text = formatKstShortDate(change.timestampMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = change.companyName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandGreen,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${change.consentTitle} ${if (change.enabled) "동의" else "해제"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                )
            }
        }
        // 1건뿐이면 카드가 화면 전체로 늘어나지 않도록 빈 칸을 채운다.
        if (changes.size == 1) Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun NoticesSection(notices: List<Notice>) {
    if (notices.isEmpty()) {
        EmptyCard("변경된 약관이 없습니다.")
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardWhite, RoundedCornerShape(12.dp))
            .padding(vertical = 4.dp),
    ) {
        notices.forEach { notice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatKstShortDate(notice.checkedAtMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = notice.companyName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = BrandGreen,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // 서버 공지에는 제목이 없어(기업명·확인시각·변경여부만) 변경 여부로 문구를 만든다.
                    Text(
                        text = if (notice.isChanged) {
                            "개인정보 처리방침이 변경되었습니다"
                        } else {
                            "변경 사항 없이 확인되었습니다"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardWhite, RoundedCornerShape(12.dp))
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

/** 로그인 기능 전까지 쓰는 데모 사용자 이름 (ApiOrganizationRepository.DEMO_USER_ID와 짝) */
private const val DEMO_USER_NAME = "김도영"
