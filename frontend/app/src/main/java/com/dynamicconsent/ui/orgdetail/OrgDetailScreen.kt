package com.dynamicconsent.ui.orgdetail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dynamicconsent.R
import com.dynamicconsent.data.model.CompanyInfo
import com.dynamicconsent.data.model.ConsentChangeRecord
import com.dynamicconsent.data.model.ConsentRequiredItem
import com.dynamicconsent.data.model.ConsentToggleItem
import com.dynamicconsent.data.model.OrganizationDetail
import com.dynamicconsent.data.model.ThirdPartyProvider
import com.dynamicconsent.ui.common.ErrorRetry
import com.dynamicconsent.ui.common.OrgLogo
import com.dynamicconsent.ui.common.RiskAnalysisSection
import com.dynamicconsent.ui.theme.AppBackground
import com.dynamicconsent.ui.theme.BrandGreen
import com.dynamicconsent.ui.theme.DividerColor
import com.dynamicconsent.ui.theme.TextPrimary
import com.dynamicconsent.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrgDetailScreen(
    orgId: String,
    initialTab: OrgDetailTab,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OrgDetailViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(orgId, initialTab) {
        viewModel.loadOrganization(orgId, initialTab)
    }

    // 토글 저장 실패·보정 안내는 화면을 막지 않고 스낵바로만 알린다.
    uiState.toggleMessage?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeToggleMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        uiState.detail?.let { detail ->
                            OrgLogo(
                                text = detail.organization.logoText,
                                backgroundColor = Color(detail.organization.logoColor),
                                size = 32.dp,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(detail.organization.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
            )
        },
    ) { innerPadding ->
        uiState.error?.let { message ->
            ErrorRetry(
                message = message,
                onRetry = viewModel::retry,
                modifier = Modifier.padding(innerPadding),
            )
            return@Scaffold
        }

        val detail = uiState.detail
        if (uiState.isLoading || detail == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val tabs = OrgDetailTab.entries
            val selectedIndex = tabs.indexOf(uiState.activeTab)
            PrimaryScrollableTabRow(selectedTabIndex = selectedIndex, edgePadding = 20.dp) {
                tabs.forEach { tab ->
                    Tab(
                        selected = tab == uiState.activeTab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.label) },
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                when (uiState.activeTab) {
                    OrgDetailTab.CONSENT -> ConsentTabContent(
                        detail = detail,
                        onConsentToggle = viewModel::toggleConsent,
                    )
                    OrgDetailTab.RISK -> RiskAnalysisSection(riskAnalysis = detail.riskAnalysis)
                    OrgDetailTab.THIRD_PARTY -> ThirdPartyTabContent(
                        orgId = detail.organization.id,
                        providers = detail.thirdPartyProviders,
                    )
                    OrgDetailTab.CHANGE_HISTORY -> ConsentHistoryTabContent(uiState.changeHistory)
                    OrgDetailTab.INFO -> InfoTabContent(detail.companyInfo)
                }
            }
        }
    }
}

@Composable
private fun ConsentTabContent(
    detail: OrganizationDetail,
    onConsentToggle: (consentId: Int, enabled: Boolean) -> Unit,
) {
    val optionalConsents = detail.consentDetail.optionalConsents
    val requiredConsents = detail.consentDetail.requiredConsents

    // 동의 항목이 아직 수집되지 않은 기업은 두 목록이 모두 비어 제목만 남는다.
    if (optionalConsents.isEmpty() && requiredConsents.isEmpty()) {
        PlaceholderContent("등록된 동의 항목이 없습니다.")
        return
    }

    Column {
        Text("선택 동의", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        if (optionalConsents.isEmpty()) {
            PlaceholderContent("철회할 수 있는 선택 동의가 없습니다.", minHeight = 80.dp)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppBackground, RoundedCornerShape(12.dp)),
            ) {
                optionalConsents.forEach { item ->
                    OptionalConsentRow(
                        item = item,
                        onToggle = { enabled -> onConsentToggle(item.id, enabled) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("필수 동의", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        if (requiredConsents.isEmpty()) {
            PlaceholderContent("등록된 필수 동의가 없습니다.", minHeight = 80.dp)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppBackground, RoundedCornerShape(12.dp)),
            ) {
                requiredConsents.forEach { item ->
                    RequiredConsentRow(item)
                }
            }
        }
    }
}

@Composable
private fun OptionalConsentRow(
    item: ConsentToggleItem,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = item.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = BrandGreen),
        )
    }
}

@Composable
private fun RequiredConsentRow(item: ConsentRequiredItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        // 필수 동의는 철회 불가 — 항상 ON 고정 표시
        Switch(
            checked = true,
            onCheckedChange = null,
            enabled = false,
            colors = SwitchDefaults.colors(disabledCheckedTrackColor = BrandGreen.copy(alpha = 0.45f)),
        )
    }
}

/** 기관별 개인정보 흐름도 이미지. 없으면 제공처 카드 목록으로 대체한다. */
private fun flowImageResFor(orgId: String): Int? = when (orgId) {
    "kakaotalk" -> R.drawable.flow_kakaotalk
    "toss" -> R.drawable.flow_toss
    "netflix" -> R.drawable.flow_netflix
    else -> null
}

@Composable
private fun ThirdPartyTabContent(
    orgId: String,
    providers: List<ThirdPartyProvider>,
) {
    val flowImageRes = flowImageResFor(orgId)
    when {
        flowImageRes != null -> {
            Image(
                painter = painterResource(id = flowImageRes),
                contentDescription = "개인정보 제3자 제공 흐름도",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        providers.isNotEmpty() -> {
            Column {
                Text("개인정보 제3자 제공 현황", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(16.dp))
                providers.forEach { provider ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .background(AppBackground, RoundedCornerShape(12.dp))
                            .padding(20.dp),
                    ) {
                        Text(provider.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        ThirdPartyRow("제공 목적", provider.purpose)
                        ThirdPartyRow("제공 항목", provider.sharedItems)
                        ThirdPartyRow("보유 기간", provider.retentionPeriod)
                    }
                }
            }
        }

        else -> PlaceholderContent("등록된 제3자 제공 정보가 없습니다.")
    }
}

@Composable
private fun ThirdPartyRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

private val HistoryApproved = Color(0xFF10B981)
private val HistoryRejected = Color(0xFFEF4444)

@Composable
private fun ConsentHistoryTabContent(history: List<ConsentChangeRecord>) {
    if (history.isEmpty()) {
        PlaceholderContent("동의 변경 내역이 없습니다.")
        return
    }
    val dateFormat = remember { SimpleDateFormat("yyyy.MM.dd", Locale.KOREA) }
    val timeFormat = remember { SimpleDateFormat("a h:mm", Locale.KOREA) }
    val grouped = history.groupBy { dateFormat.format(Date(it.timestampMillis)) }

    Column {
        grouped.forEach { (date, records) ->
            Text(date, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .background(AppBackground, RoundedCornerShape(12.dp)),
            ) {
                records.forEach { record ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val statusColor = if (record.enabled) HistoryApproved else HistoryRejected
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(statusColor, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (record.enabled) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = if (record.enabled) "동의" else "해제",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${record.consentTitle} ${if (record.enabled) "동의" else "해제"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                            )
                            Text(
                                text = timeFormat.format(Date(record.timestampMillis)),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoTabContent(companyInfo: CompanyInfo) {
    Column {
        Text("기업정보", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppBackground, RoundedCornerShape(12.dp))
                .padding(20.dp),
        ) {
            InfoRow("서비스명", companyInfo.serviceName)
            InfoRow("법인명", companyInfo.legalName)
            InfoRow("개인정보보호 인증항목", companyInfo.privacyCertification)
            InfoRow("개인정보 처리방침", "바로가기", isLink = true)
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("기업 뉴스", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        PlaceholderContent("등록된 뉴스가 없습니다.", minHeight = 80.dp)

        Spacer(modifier = Modifier.height(32.dp))
        Text("최근 보안 사고 이력", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(12.dp))
        PlaceholderContent("등록된 이력이 없습니다.", minHeight = 80.dp)
    }
}

@Composable
private fun InfoRow(label: String, value: String, isLink: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isLink) BrandGreen else TextPrimary,
        )
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
}

@Composable
private fun PlaceholderContent(text: String, minHeight: Dp = 200.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(minHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}
