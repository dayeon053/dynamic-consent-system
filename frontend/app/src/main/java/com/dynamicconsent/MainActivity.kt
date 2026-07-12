package com.dynamicconsent

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.core.util.Consumer
import androidx.navigation.compose.rememberNavController
import com.dynamicconsent.ui.navigation.AppNavHost
import com.dynamicconsent.ui.theme.FrontendTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FrontendTheme {
                val navController = rememberNavController()

                // launchMode=singleTask라 앱이 이미 떠 있는 상태에서 딥링크가 오면
                // 새 Activity 대신 onNewIntent로 전달된다. 이를 NavController에 넘겨 화면을 전환한다.
                DisposableEffect(navController) {
                    val listener = Consumer<Intent> { intent ->
                        navController.handleDeepLink(intent)
                    }
                    addOnNewIntentListener(listener)
                    onDispose { removeOnNewIntentListener(listener) }
                }

                AppNavHost(
                    navController = navController,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
