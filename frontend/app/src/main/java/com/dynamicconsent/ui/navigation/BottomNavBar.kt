package com.dynamicconsent.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dynamicconsent.ui.theme.BrandGreen
import com.dynamicconsent.ui.theme.TextSecondary

/** 선택된 탭 뒤에 깔리는 옅은 초록 (브랜드 초록의 연한 톤) */
private val SelectedTabIndicator = Color(0xFFE3F1E8)

/**
 * 하단 탭 3개. 이 화면들에서만 탭바가 보인다.
 *
 * [route]는 실제로 이동할 주소, [matchRoute]는 현재 위치를 판별할 라우트 패턴이다.
 * 위험기관리스트처럼 선택 인자가 있는 화면은 둘이 다르다
 * (이동은 "risk_list", 패턴은 "risk_list?category={category}").
 */
enum class BottomTab(
    val route: String,
    val matchRoute: String,
    val label: String,
    val icon: ImageVector,
) {
    ORGANIZATIONS(
        route = Screen.RiskList.createRoute(),
        matchRoute = Screen.RiskList.route,
        label = "기관",
        icon = Icons.AutoMirrored.Filled.List,
    ),
    HOME(
        route = Screen.Home.route,
        matchRoute = Screen.Home.route,
        label = "홈",
        icon = Icons.Default.Home,
    ),
    MY(
        route = Screen.Monitor.route,
        matchRoute = Screen.Monitor.route,
        label = "MY",
        icon = Icons.Default.Person,
    ),
}

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onTabClick: (BottomTab) -> Unit,
) {
    // Material 기본 컨테이너 색(보라 계열)이 브랜드와 어긋나 흰 배경 + 브랜드 초록으로 맞춘다.
    NavigationBar(containerColor = Color.White) {
        BottomTab.entries.forEach { tab ->
            val selected = currentRoute == tab.matchRoute
            NavigationBarItem(
                selected = selected,
                onClick = { onTabClick(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BrandGreen,
                    selectedTextColor = BrandGreen,
                    indicatorColor = SelectedTabIndicator,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                ),
            )
        }
    }
}
