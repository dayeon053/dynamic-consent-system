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

        /** 오버레이 팝업 등 외부에서 기업상세로 진입하는 딥링크. AndroidManifest의 intent-filter와 짝을 이룬다. */
        const val DEEP_LINK_URI_PATTERN = "dynamicconsent://org/{orgId}?tab={tab}"

        fun createRoute(orgId: String, tab: OrgDetailTab = OrgDetailTab.CONSENT) =
            "org_detail/$orgId?tab=${tab.name}"

        fun createDeepLinkUri(orgId: String, tab: OrgDetailTab = OrgDetailTab.CONSENT) =
            "dynamicconsent://org/$orgId?tab=${tab.name}"
    }
}
