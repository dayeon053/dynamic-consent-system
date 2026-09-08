package com.dynamicconsent.ui.navigation

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.dynamicconsent.ui.home.HomeScreen
import com.dynamicconsent.ui.monitor.MonitorScreen
import com.dynamicconsent.ui.notice.NoticeScreen
import com.dynamicconsent.ui.orgdetail.OrgDetailScreen
import com.dynamicconsent.ui.orgdetail.OrgDetailTab
import com.dynamicconsent.ui.risk.RiskListScreen

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    /**
     * 상단 뒤로가기 화살표를 시스템 뒤로가기와 똑같이 동작시킨다.
     * 되돌아갈 화면이 있으면 그리로 가고, 시작 화면이면 앱을 벗어난다.
     */
    val onBackClick: () -> Unit = { backDispatcher?.onBackPressed() }

    val currentRoute by navController.currentBackStackEntryAsState()
    val route = currentRoute?.destination?.route
    val showBottomBar = BottomTab.entries.any { it.matchRoute == route }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = route,
                    onTabClick = { tab ->
                        navController.navigate(tab.route) {
                            // 탭을 옮길 때마다 홈 위로 한 겹만 쌓이게 정리한다.
                            // saveState/restoreState는 쓰지 않는다 — 위험기관리스트가 홈에서
                            // 밀고 올라간 화면이라, 저장했다가 되살리면 홈 탭을 눌러도
                            // 위험기관리스트가 다시 복원돼 홈으로 못 간다.
                            popUpTo(Screen.Home.route)
                            launchSingleTop = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onRiskListClick = { navController.navigate(Screen.RiskList.createRoute()) },
                    onCategoryClick = { category ->
                        navController.navigate(Screen.RiskList.createRoute(category))
                    },
                    onNoticeClick = { navController.navigate(Screen.Notice.route) },
                )
            }

            composable(
                route = Screen.RiskList.route,
                arguments = listOf(
                    navArgument(Screen.RiskList.ARG_CATEGORY) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                RiskListScreen(
                    category = backStackEntry.arguments?.getString(Screen.RiskList.ARG_CATEGORY),
                    onBackClick = onBackClick,
                    onOrgDetailClick = { orgId ->
                        navController.navigate(Screen.OrgDetail.createRoute(orgId, OrgDetailTab.RISK))
                    },
                    onNoticeClick = { navController.navigate(Screen.Notice.route) },
                )
            }

            composable(Screen.Monitor.route) {
                MonitorScreen(onBackClick = onBackClick)
            }

            composable(Screen.Notice.route) {
                NoticeScreen(onBackClick = onBackClick)
            }

            composable(
                route = Screen.OrgDetail.route,
                arguments = listOf(
                    navArgument(Screen.OrgDetail.ARG_ORG_ID) { type = NavType.StringType },
                    navArgument(Screen.OrgDetail.ARG_TAB) {
                        type = NavType.StringType
                        defaultValue = OrgDetailTab.CONSENT.name
                    },
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = Screen.OrgDetail.DEEP_LINK_URI_PATTERN },
                ),
            ) { backStackEntry ->
                val orgId = backStackEntry.arguments?.getString(Screen.OrgDetail.ARG_ORG_ID).orEmpty()
                val tabName = backStackEntry.arguments?.getString(Screen.OrgDetail.ARG_TAB)
                    ?: OrgDetailTab.CONSENT.name
                val initialTab =
                    OrgDetailTab.entries.firstOrNull { it.name == tabName } ?: OrgDetailTab.CONSENT
                OrgDetailScreen(
                    orgId = orgId,
                    initialTab = initialTab,
                    onBackClick = onBackClick,
                )
            }
        }
    }
}
