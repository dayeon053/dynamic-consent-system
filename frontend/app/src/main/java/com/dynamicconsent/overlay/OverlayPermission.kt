package com.dynamicconsent.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** 오버레이(다른 앱 위에 표시) 권한 보유 여부. API 23 미만은 매니페스트 선언만으로 자동 부여. */
fun canDrawOverlays(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true

/**
 * 오버레이 권한을 확인하고, 없으면 설정 화면으로 유도한 뒤 결과를 콜백으로 돌려주는 Compose 헬퍼.
 *
 * SYSTEM_ALERT_WINDOW는 일반 런타임 권한과 달리 다이얼로그로 요청할 수 없고,
 * ACTION_MANAGE_OVERLAY_PERMISSION 설정 화면을 열어야 한다. 또 복귀 시 resultCode를
 * 신뢰할 수 없어 canDrawOverlays()로 재확인한다.
 *
 * 사용 예:
 *   val request = rememberOverlayPermissionRequest { granted -> if (granted) start() }
 *   Button(onClick = { request() }) { Text("오버레이 띄우기") }
 */
@Composable
fun rememberOverlayPermissionRequest(
    onResult: (granted: Boolean) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onResult(canDrawOverlays(context))
    }
    return {
        if (canDrawOverlays(context)) {
            onResult(true)
        } else {
            launcher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    }
}
