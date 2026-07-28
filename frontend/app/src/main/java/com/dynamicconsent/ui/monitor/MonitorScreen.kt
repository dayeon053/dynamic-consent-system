package com.dynamicconsent.ui.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dynamicconsent.monitor.AppLaunchMonitorService
import com.dynamicconsent.monitor.RiskOverlayPipeline
import com.dynamicconsent.monitor.hasUsageAccess
import com.dynamicconsent.monitor.isIgnoringBatteryOptimizations
import com.dynamicconsent.monitor.openUsageAccessSettings
import com.dynamicconsent.monitor.requestIgnoreBatteryOptimizations
import com.dynamicconsent.overlay.canDrawOverlays
import com.dynamicconsent.overlay.rememberOverlayPermissionRequest
import com.dynamicconsent.ui.theme.TextPrimary
import com.dynamicconsent.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * [2·3단계 테스트 화면] 감시 파이프라인 동작 확인용.
 *
 *  - 권한 상태 확인 및 설정 화면 이동 (사용 정보 접근 / 오버레이)
 *  - 백그라운드 감시 시작/중지 (AppLaunchMonitorService)
 *  - 테스트 오버레이 강제 표시 (감지 없이 파이프라인만 직접 실행)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pipeline = remember { RiskOverlayPipeline(context.applicationContext) }

    var usageGranted by remember { mutableStateOf(hasUsageAccess(context)) }
    var overlayGranted by remember { mutableStateOf(canDrawOverlays(context)) }
    var monitoring by remember { mutableStateOf(AppLaunchMonitorService.isRunning) }
    var batteryExempt by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }

    // 감시 대상은 서버(또는 mock) 기관 목록에서 런타임에 구성된다.
    var watchedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) {
        runCatching { pipeline.prepareWatchedApps() }
        watchedPackages = pipeline.watchedPackages
    }

    // 설정 화면에 다녀오면(ON_RESUME) 권한 상태를 다시 읽는다
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                usageGranted = hasUsageAccess(context)
                overlayGranted = canDrawOverlays(context)
                monitoring = AppLaunchMonitorService.isRunning
                batteryExempt = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val requestOverlayPermission = rememberOverlayPermissionRequest { granted ->
        overlayGranted = granted
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("실행 감시 테스트", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("1. 권한", style = MaterialTheme.typography.titleMedium, color = TextPrimary)

            PermissionRow(
                label = "사용 정보 접근",
                granted = usageGranted,
                onRequest = { openUsageAccessSettings(context) },
            )
            PermissionRow(
                label = "다른 앱 위에 표시",
                granted = overlayGranted,
                onRequest = requestOverlayPermission,
            )

            PermissionRow(
                label = "배터리 최적화 예외",
                granted = batteryExempt,
                onRequest = { requestIgnoreBatteryOptimizations(context) },
            )
            Text(
                "예외로 등록해야 화면이 꺼진 뒤에도 감지가 끊기지 않습니다. " +
                    "제조사 자체 절전 기능은 별도로 해제해야 할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )

            HorizontalDivider()

            Text("2. 백그라운드 감시", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                "감시 대상: ${watchedPackages.ifEmpty { setOf("불러오는 중…") }.joinToString()}\n" +
                    "감시 중에 대상 앱을 실행하면 위험도 팝업이 뜹니다.\n" +
                    "한 번 켜두면 재부팅하거나 최근 앱에서 앱을 지워도 자동으로 다시 시작됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Button(
                onClick = {
                    if (monitoring) {
                        AppLaunchMonitorService.stop(context)
                        monitoring = false
                    } else {
                        AppLaunchMonitorService.start(context)
                        monitoring = true
                    }
                },
                enabled = usageGranted && overlayGranted,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (monitoring) "감시 중지" else "감시 시작")
            }

            HorizontalDivider()

            Text("3. 테스트", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            val testPackage = watchedPackages.firstOrNull()
            OutlinedButton(
                onClick = {
                    testPackage?.let { pkg -> scope.launch { pipeline.showOverlayFor(pkg) } }
                },
                enabled = overlayGranted && testPackage != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (testPackage != null) "테스트 오버레이 바로 띄우기 ($testPackage)"
                    else "감시 대상을 불러오는 중…",
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    onRequest: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(
                if (granted) "허용됨" else "허용 필요",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
        if (!granted) {
            OutlinedButton(onClick = onRequest) { Text("설정 열기") }
        }
    }
}
