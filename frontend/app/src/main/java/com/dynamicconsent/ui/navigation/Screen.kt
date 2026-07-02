package com.dynamicconsent.ui.navigation

import com.dynamicconsent.ui.orgdetail.OrgDetailTab

/**
 * 앱의 화면(라우트) 정의.
 */
sealed class Screen(val route: String) {
    data object RiskList : Screen("risk_list")

    data object OrgDetail : Screen("org_detail/{orgId}?tab={tab}") {
        const val ARG_ORG_ID = "orgId"
        const val ARG_TAB = "tab"

        fun createRoute(orgId: String, tab: OrgDetailTab = OrgDetailTab.CONSENT) =
            "org_detail/$orgId?tab=${tab.name}"
    }
}
