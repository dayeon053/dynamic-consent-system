package com.dynamicconsent.ui.orgdetail

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dynamicconsent.data.model.CompanyInfo
import com.dynamicconsent.data.model.ConsentRequiredItem
import com.dynamicconsent.data.model.ConsentToggleItem
import com.dynamicconsent.data.model.OrganizationDetail
import com.dynamicconsent.ui.common.OrgLogo
import com.dynamicconsent.ui.common.RiskAnalysisSection
import com.dynamicconsent.ui.theme.AppBackground
import com.dynamicconsent.ui.theme.BrandGreen
import com.dynamicconsent.ui.theme.DividerColor
import com.dynamicconsent.ui.theme.TextPrimary
import com.dynamicconsent.ui.theme.TextSecondary

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

    LaunchedEffect(orgId, initialTab) {
        viewModel.loadOrganization(orgId, initialTab)
    }

    Scaffold(
        modifier = modifier,
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
                    OrgDetailTab.CONSENT -> ConsentTabContent(detail)
                    OrgDetailTab.RISK -> RiskAnalysisSection(riskAnalysis = detail.riskAnalysis)
                    OrgDetailTab.THIRD_PARTY -> PlaceholderContent("제3자 제공 정보")
                    OrgDetailTab.CHANGE_HISTORY -> PlaceholderContent("동의 변경 내역이 없습니다.")
                    OrgDetailTab.INFO -> InfoTabContent(detail.companyInfo)
                }
            }
        }
    }
}

@Composable
private fun ConsentTabContent(detail: OrganizationDetail) {
    Column {
        Text("선택 동의", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppBackground, RoundedCornerShape(12.dp)),
        ) {
            detail.consentDetail.optionalConsents.forEach { item ->
                OptionalConsentRow(item)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("필수 동의", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppBackground, RoundedCornerShape(12.dp)),
        ) {
            detail.consentDetail.requiredConsents.forEach { item ->
                RequiredConsentRow(item)
            }
        }
    }
}

@Composable
private fun OptionalConsentRow(item: ConsentToggleItem) {
    var isEnabled by remember(item.id) { mutableStateOf(item.enabled) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = isEnabled,
            onCheckedChange = { isEnabled = it },
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
    ) {
        Text(item.title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
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
