package com.dynamicconsent.ui.risk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dynamicconsent.data.model.Organization
import com.dynamicconsent.data.model.OrganizationDetail
import com.dynamicconsent.ui.common.EmptyState
import com.dynamicconsent.ui.common.ErrorRetry
import com.dynamicconsent.ui.common.OrgLogo
import com.dynamicconsent.ui.common.RiskAnalysisSection
import com.dynamicconsent.ui.theme.BrandGreen
import com.dynamicconsent.ui.theme.DividerColor
import com.dynamicconsent.ui.theme.TextPrimary
import com.dynamicconsent.ui.theme.accentColor
import com.dynamicconsent.ui.theme.backgroundColor

private val OfflineBannerBackground = Color(0xFFFFF4E5)
private val OfflineBannerText = Color(0xFF8A5300)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiskListScreen(
    onBackClick: () -> Unit,
    onOrgDetailClick: (orgId: String) -> Unit,
    onMonitorClick: () -> Unit = {},
    onNoticeClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: RiskListViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("위험기관리스트", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Info, contentDescription = "정보", tint = BrandGreen)
                    }
                    IconButton(onClick = onNoticeClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = "공지사항",
                            tint = BrandGreen,
                        )
                    }
                    // 벨 아이콘은 재은님의 감시 테스트 화면으로 간다 (기존 동작 유지)
                    IconButton(onClick = onMonitorClick) {
                        Icon(Icons.Default.Notifications, contentDescription = "알림", tint = BrandGreen)
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

        // 불러오기는 성공했는데 기업이 0건인 경우. 오류 화면을 띄우면 사용자가
        // 앱이 고장난 것으로 오해하므로 빈 상태로 따로 안내한다.
        if (uiState.organizations.isEmpty()) {
            EmptyState(
                message = "표시할 기업이 없습니다.",
                description = "등록된 기업이 아직 없거나 분석이 끝나지 않았습니다.",
                onAction = viewModel::retry,
                modifier = Modifier.padding(innerPadding),
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (uiState.isFallback) {
                OfflineDataBanner()
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.organizations, key = { it.id }) { org ->
                    RiskOrgCard(
                        organization = org,
                        isSelected = org.id == uiState.selectedOrganizationId,
                        onClick = { viewModel.selectOrganization(org.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            val detail = uiState.selectedDetail
            if (detail != null) {
                RiskDetailCard(
                    detail = detail,
                    onLearnMoreClick = { onOrgDetailClick(detail.organization.id) },
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp),
                )
            }
        }
    }
}

/**
 * 서버 대신 mock으로 채워졌음을 알리는 띠.
 *
 * mock은 사용자의 동의 철회가 반영되지 않은 **초기 상태값**이라, 표시 없이 보여주면
 * 사용자가 내린 점수가 원래대로 돌아간 것처럼 보인다.
 */
@Composable
private fun OfflineDataBanner() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp)
            .background(OfflineBannerBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = "오프라인 데이터",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = OfflineBannerText,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "서버에 연결하지 못해 저장된 예시 데이터를 보여주고 있습니다. 내가 바꾼 동의 상태는 반영되지 않았습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = OfflineBannerText,
        )
    }
}

@Composable
private fun RiskOrgCard(
    organization: Organization,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(100.dp)
            .height(160.dp)
            .background(organization.riskGrade.backgroundColor, RoundedCornerShape(12.dp))
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, BrandGreen, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            OrgLogo(text = organization.logoText, backgroundColor = Color(organization.logoColor), size = 32.dp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(organization.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
        }
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${organization.riskScore}점",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = organization.riskGrade.accentColor,
            )
            Text(
                text = organization.riskGrade.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = organization.riskGrade.accentColor,
            )
        }
    }
}

@Composable
private fun RiskDetailCard(
    detail: OrganizationDetail,
    onLearnMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, BrandGreen, RoundedCornerShape(12.dp))
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrgLogo(
                text = detail.organization.logoText,
                backgroundColor = Color(detail.organization.logoColor),
                size = 40.dp,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(detail.organization.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DividerColor),
        )
        Spacer(modifier = Modifier.height(24.dp))

        RiskAnalysisSection(riskAnalysis = detail.riskAnalysis)

        TextButton(
            onClick = onLearnMoreClick,
            modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
        ) {
            Text("더 알아보기", color = BrandGreen, fontWeight = FontWeight.SemiBold)
        }
    }
}
