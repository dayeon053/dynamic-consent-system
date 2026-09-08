package com.dynamicconsent.ui.navigation

import android.net.Uri
import com.dynamicconsent.ui.orgdetail.OrgDetailTab

/**
 * 앱의 화면(라우트) 정의.
 */
sealed class Screen(val route: String) {
    /** 시작 화면 */
    data object Home : Screen("home")

    /**
     * 위험기관리스트. 홈의 카테고리 바로가기로 들어오면 [ARG_CATEGORY]로 걸러 보여준다.
     * 한글 카테고리가 경로에 들어가므로 만들 때 URL 인코딩한다.
     */
    data object RiskList : Screen("risk_list?category={category}") {
        const val ARG_CATEGORY = "category"

        fun createRoute(category: String? = null): String =
            if (category.isNullOrBlank()) "risk_list" else "risk_list?category=${Uri.encode(category)}"
    }

    /** 실행 감시 설정 (하단 MY 탭) */
    data object Monitor : Screen("monitor")

    /** 약관 변경 알림 목록 (공지사항) */
    data object Notice : Screen("notice")

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
