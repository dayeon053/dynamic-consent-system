package com.dynamicconsent.ui.navigation

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
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
     *
     * 앱에 홈 화면이 따로 없어 위험기관리스트가 시작 화면인데, 여기서 popBackStack()은
     * 되돌아갈 화면이 없어 아무 일도 하지 않는다(화살표가 먹통으로 보인다).
     * 시스템 뒤로가기로 넘기면 되돌아갈 화면이 있으면 그리로 가고, 없으면 앱을 벗어난다.
     */
    val onBackClick: () -> Unit = { backDispatcher?.onBackPressed() }

    NavHost(
        navController = navController,
        startDestination = Screen.RiskList.route,
        modifier = modifier,
    ) {
        composable(Screen.RiskList.route) {
            RiskListScreen(
                onBackClick = onBackClick,
                onOrgDetailClick = { orgId ->
                    navController.navigate(Screen.OrgDetail.createRoute(orgId, OrgDetailTab.RISK))
                },
                onMonitorClick = { navController.navigate(Screen.Monitor.route) },
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
            val initialTab = OrgDetailTab.entries.firstOrNull { it.name == tabName } ?: OrgDetailTab.CONSENT
            OrgDetailScreen(
                orgId = orgId,
                initialTab = initialTab,
                onBackClick = onBackClick,
            )
        }
    }
}
