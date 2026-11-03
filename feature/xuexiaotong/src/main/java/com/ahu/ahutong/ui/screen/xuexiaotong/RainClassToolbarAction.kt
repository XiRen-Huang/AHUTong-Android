package com.ahu.ahutong.ui.screen.xuexiaotong

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.ahutong.ui.components.AppButton
import com.ahu.ahutong.ui.components.AppButtonVariant
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import com.ahu.ahutong.ui.components.AppModalBottomSheet
import java.util.Calendar

/**
 * 雨课堂入口，塞进日历页右上角的操作胶囊里。
 *
 * 状态、同步、退出和扫码登录都在这里，不再占用日历上方的整块卡片空间。
 */
@Composable
fun RainClassToolbarAction(
    viewModel: RainClassViewModel,
    compact: Boolean = true,
    modifier: Modifier = Modifier
) {
    val loggedIn by viewModel.loggedIn.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val progress by viewModel.syncProgress.collectAsState()
    val lastSync by viewModel.lastSync.collectAsState()
    val works by viewModel.works.collectAsState()
    val error by viewModel.error.collectAsState()
    val qrPng by viewModel.qrPng.collectAsState()
    val qrStatus by viewModel.qrStatus.collectAsState()

    var showSheet by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(loggedIn) {
        if (loggedIn) showQr = false
    }

    val buttonSize: Dp = if (compact) 38.dp else 48.dp
    val iconSize: Dp = if (compact) 17.dp else 24.dp

    Box(modifier = modifier.size(buttonSize)) {
        IconButton(
            onClick = { showSheet = true },
            modifier = Modifier.size(buttonSize)
        ) {
            if (syncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(iconSize),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.School,
                    contentDescription = "雨课堂",
                    tint = if (loggedIn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        if (loggedIn && works.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-1).dp, y = 1.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    "${works.size}",
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }

    if (showSheet) {
        AppModalBottomSheet(
            title = "雨课堂",
            onDismissRequest = { showSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (loggedIn) "已登录 · ${works.size} 项任务" else "未登录",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (loggedIn) {
                    Text(
                        lastSyncLabel(lastSync),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (syncing && progress.message.isNotBlank()) {
                    Text(
                        progress.message,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                error?.let { message ->
                    Text(
                        message,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (loggedIn) {
                        AppButton(
                            onClick = viewModel::syncNow,
                            enabled = !syncing,
                            variant = AppButtonVariant.Secondary
                        ) {
                            Text(if (syncing) "同步中" else "同步", fontSize = 13.sp)
                        }
                        AppButton(
                            onClick = { confirmLogout = true },
                            variant = AppButtonVariant.Secondary
                        ) {
                            Text("退出", fontSize = 13.sp)
                        }
                    } else {
                        AppButton(
                            onClick = {
                                showSheet = false
                                showQr = true
                                viewModel.beginLogin()
                            },
                            variant = AppButtonVariant.Primary
                        ) {
                            Text("登录雨课堂", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    if (showQr) {
        RainClassQrDialog(
            png = qrPng,
            status = qrStatus,
            onRefresh = viewModel::beginLogin,
            onDismiss = {
                viewModel.cancelLogin()
                showQr = false
            }
        )
    }

    if (confirmLogout) {
        AppDialog(
            title = "退出雨课堂",
            onDismiss = { confirmLogout = false },
            subtitle = "会清除雨课堂登录态和本地缓存，现有提醒也会取消。",
            actions = listOf(
                AppDialogAction(
                    label = "取消",
                    onClick = { confirmLogout = false },
                    style = AppDialogActionStyle.Neutral
                ),
                AppDialogAction(
                    label = "退出",
                    onClick = {
                        confirmLogout = false
                        showSheet = false
                        viewModel.logout()
                    },
                    style = AppDialogActionStyle.Danger
                )
            )
        )
    }
}

private fun lastSyncLabel(lastSync: Long): String {
    if (lastSync <= 0L) return "尚未同步"
    val c = Calendar.getInstance().apply { timeInMillis = lastSync }
    val hh = c.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
    val mm = c.get(Calendar.MINUTE).toString().padStart(2, '0')
    return "上次同步：${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日 $hh:$mm"
}
