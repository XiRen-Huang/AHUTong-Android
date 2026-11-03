package com.ahu.ahutong.ui.state

import com.ahu.ahutong.data.rainclass.RainClassSession
import com.ahu.ahutong.data.rainclass.RainClassStore
import com.ahu.ahutong.data.xuexiaotong.ChaoxingReminderStore
import com.ahu.ahutong.data.xuexiaotong.CustomEvent
import com.ahu.ahutong.data.xuexiaotong.RemindSetting
import com.ahu.ahutong.data.xuexiaotong.Store
import com.ahu.ahutong.data.xuexiaotong.Work
import com.ahu.ahutong.feature.xuexiaotong.rainclass.toCalendarWork
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 现有提醒调度只认识一个窄的 [ChaoxingReminderStore]。
 *
 * 这里把超星与雨课堂的作业合并，`:background` 因此完全不用知道雨课堂的存在；
 * 自建日程、提醒设置和已排提醒的记账仍全部委托给原来的 Store。
 */
@Singleton
class AppReminderStore @Inject constructor(
    private val rainClassSession: RainClassSession,
    private val rainClassStore: RainClassStore
) : ChaoxingReminderStore {

    override fun getWorks(): List<Work> = buildList {
        if (Store.hasCookie()) addAll(Store.getWorks())
        if (rainClassSession.hasSession()) {
            addAll(rainClassStore.assignments().map { it.toCalendarWork() })
        }
    }

    override fun getCustomEvents(): List<CustomEvent> = Store.getCustomEvents()

    override fun getRemindSetting(): RemindSetting = Store.getRemindSetting()

    override fun getRemindedMap(): Map<String, Int> = Store.getRemindedMap()

    override fun saveRemindedMap(map: Map<String, Int>) = Store.saveRemindedMap(map)

    override fun hasCookie(): Boolean =
        Store.hasCookie() || rainClassSession.hasSession()
}
