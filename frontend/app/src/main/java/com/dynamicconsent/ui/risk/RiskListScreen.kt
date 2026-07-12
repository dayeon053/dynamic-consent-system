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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
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
import com.dynamicconsent.ui.common.OrgLogo
import com.dynamicconsent.ui.common.RiskAnalysisSection
import com.dynamicconsent.ui.theme.BrandGreen
import com.dynamicconsent.ui.theme.DividerColor
import com.dynamicconsent.ui.theme.TextPrimary
import com.dynamicconsent.ui.theme.accentColor
import com.dynamicconsent.ui.theme.backgroundColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiskListScreen(
    onBackClick: () -> Unit,
    onOrgDetailClick: (orgId: String) -> Unit,
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
                    IconButton(onClick = {}) {
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.organizations, key = { it.id }) { org ->
                    RiskOrgCard(
                        organization = org,
                        isSelected = org.id == uiState.selectedOrganizationId,
                        onClick = { viewModel.selectOrganization(org.id) },
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

@Composable
private fun RiskOrgCard(
    organization: Organization,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
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
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = organization.riskGrade.accentColor, modifier = Modifier.size(28.dp))
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
