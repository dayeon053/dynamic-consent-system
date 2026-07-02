package com.dynamicconsent.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dynamicconsent.ui.orgdetail.OrgDetailScreen
import com.dynamicconsent.ui.orgdetail.OrgDetailTab
import com.dynamicconsent.ui.risk.RiskListScreen

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Screen.RiskList.route,
        modifier = modifier,
    ) {
        composable(Screen.RiskList.route) {
            RiskListScreen(
                onBackClick = { navController.popBackStack() },
                onOrgDetailClick = { orgId ->
                    navController.navigate(Screen.OrgDetail.createRoute(orgId, OrgDetailTab.RISK))
                },
            )
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
        ) { backStackEntry ->
            val orgId = backStackEntry.arguments?.getString(Screen.OrgDetail.ARG_ORG_ID).orEmpty()
            val tabName = backStackEntry.arguments?.getString(Screen.OrgDetail.ARG_TAB)
                ?: OrgDetailTab.CONSENT.name
            val initialTab = OrgDetailTab.entries.firstOrNull { it.name == tabName } ?: OrgDetailTab.CONSENT
            OrgDetailScreen(
                orgId = orgId,
                initialTab = initialTab,
                onBackClick = { navController.popBackStack() },
            )
        }
    }
}
