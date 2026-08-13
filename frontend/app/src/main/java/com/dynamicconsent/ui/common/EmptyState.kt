package com.dynamicconsent.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dynamicconsent.ui.theme.TextSecondary

/**
 * 오류가 아니라 **보여줄 데이터가 없을 때** 쓰는 화면.
 *
 * [ErrorRetry]와 구분해서 쓴다 — 저쪽은 불러오기가 실패한 상황이고, 이쪽은 정상적으로
 * 불러왔는데 결과가 0건인 상황이라 문구도 버튼도 달라야 한다("다시 시도" vs "새로고침").
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionLabel: String = "새로고침",
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        onAction?.let {
            OutlinedButton(onClick = it) { Text(actionLabel) }
        }
    }
}
