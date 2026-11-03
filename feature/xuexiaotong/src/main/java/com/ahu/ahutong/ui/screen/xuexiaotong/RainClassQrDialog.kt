package com.ahu.ahutong.ui.screen.xuexiaotong

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.ahutong.data.rainclass.RainClassQrStatus
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle

@Composable
fun RainClassQrDialog(
    png: ByteArray?,
    status: RainClassQrStatus?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val bitmap = remember(png) {
        png?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        }
    }
    val expired = status is RainClassQrStatus.Expired
    val failed = status is RainClassQrStatus.Failed

    AppDialog(
        title = "扫码登录雨课堂",
        onDismiss = onDismiss,
        subtitle = "用微信扫描二维码；同一台手机可先截图，再从微信「扫一扫 → 相册」识别。",
        actions = buildList {
            if (expired || failed) {
                add(
                    AppDialogAction(
                        label = "刷新二维码",
                        onClick = onRefresh,
                        style = AppDialogActionStyle.Primary
                    )
                )
            }
            add(
                AppDialogAction(
                    label = "取消",
                    onClick = onDismiss,
                    style = AppDialogActionStyle.Neutral
                )
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    bitmap != null -> Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "雨课堂登录二维码",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )

                    failed -> Text(
                        text = "二维码获取失败",
                        color = Color(0xFFB3261E),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    expired -> Text(
                        text = "二维码已过期\n点下方「刷新二维码」重新获取",
                        color = Color(0xFFB3261E),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    else -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Text(
                            text = "正在获取二维码…",
                            color = Color(0xFF49454F),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Text(
                text = statusLabel(status),
                fontSize = 14.sp,
                color = when {
                    failed -> MaterialTheme.colorScheme.error
                    expired -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            if (status is RainClassQrStatus.Scanned) {
                Text(
                    text = "请在微信中确认登录",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun statusLabel(status: RainClassQrStatus?): String = when (status) {
    RainClassQrStatus.Waiting, null -> "等待扫码"
    RainClassQrStatus.Scanned -> "已扫码"
    RainClassQrStatus.Expired -> "二维码已过期"
    RainClassQrStatus.Cancelled -> "已取消"
    is RainClassQrStatus.Failed -> status.message
}
