package com.ahu.ahutong.ui.screen.xuexiaotong

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahu.ahutong.data.xuexiaotong.Work
import com.ahu.ahutong.ui.components.AppDialog
import com.ahu.ahutong.ui.components.AppDialogAction
import com.ahu.ahutong.ui.components.AppDialogActionStyle
import java.util.Calendar

@Composable
fun RainClassWorkDetailDialog(
    work: Work,
    onDismiss: () -> Unit,
    onToggleDone: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current

    AppDialog(
        title = work.title,
        onDismiss = onDismiss,
        subtitle = work.courseName,
        titleFontWeight = null,
        titleMaxLines = 2,
        contentSpacing = 16.dp,
        actions = buildList {
            onToggleDone?.let { toggle ->
                add(
                    AppDialogAction(
                        label = if (work.isDone) "撤销已提交标记" else "标记为已提交",
                        onClick = { toggle(!work.isDone) },
                        style = AppDialogActionStyle.Neutral
                    )
                )
            }
            add(
                AppDialogAction(
                    label = "打开雨课堂网页",
                    onClick = {
                        val opened = runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://changjiang.yuketang.cn")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.isSuccess
                        if (!opened) {
                            Toast.makeText(
                                context,
                                "没有可打开网页的应用",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    style = AppDialogActionStyle.Primary
                )
            )
            add(
                AppDialogAction(
                    label = "关闭",
                    onClick = onDismiss,
                    style = AppDialogActionStyle.Neutral
                )
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (work.isDone) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when (work.status) {
                        "已完成" -> "已完成"
                        "未完成" -> "未完成"
                        else -> "完成状态未知"
                    },
                    fontSize = 14.sp,
                    color = if (work.isDone) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            work.startTs?.let { ts ->
                TimeRow(label = "开始时间", ts = ts, emphasized = false)
            }
            work.endTs?.let { ts ->
                TimeRow(label = "截止时间", ts = ts, emphasized = true)
            }
        }
    }
}

@Composable
private fun TimeRow(label: String, ts: Long, emphasized: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = formatRainClassTime(ts),
            fontSize = 14.sp,
            fontWeight = if (emphasized) FontWeight.SemiBold else null
        )
    }
}

private fun formatRainClassTime(ts: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    val hh = c.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
    val mm = c.get(Calendar.MINUTE).toString().padStart(2, '0')
    return "${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日 $hh:$mm"
}
