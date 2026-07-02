package com.dynamicconsent.ui.navigation

/**
 * 앱의 화면(라우트) 정의.
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")

    data object Detail : Screen("detail/{itemId}") {
        const val ARG_ITEM_ID = "itemId"

        fun createRoute(itemId: String) = "detail/$itemId"
    }
}
