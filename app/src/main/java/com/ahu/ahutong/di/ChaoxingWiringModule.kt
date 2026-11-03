package com.ahu.ahutong.di

import com.ahu.ahutong.data.xuexiaotong.ChaoxingReminders
import com.ahu.ahutong.data.xuexiaotong.ChaoxingSession
import com.ahu.ahutong.data.xuexiaotong.ChaoxingStore
import com.ahu.ahutong.data.xuexiaotong.ChaoxingReminderStore
import com.ahu.ahutong.ui.state.AppChaoxingReminders
import com.ahu.ahutong.ui.state.AppChaoxingSession
import com.ahu.ahutong.ui.state.AppChaoxingStore
import com.ahu.ahutong.ui.state.AppReminderStore
import com.ahu.ahutong.ui.screen.xuexiaotong.XuexiaotongDispatcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 学习通 feature 的接线：端口在 :data:chaoxing，实现留在 :app（协议客户端、静态存储、提醒）。
 *
 * 只有组合根同时认识两边——学习通的界面因此看不见 Cookie、存储键，也看不见提醒调度。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ChaoxingWiringModule {

    @Binds
    @Singleton
    abstract fun bindChaoxingSession(implementation: AppChaoxingSession): ChaoxingSession

    @Binds
    @Singleton
    abstract fun bindChaoxingStore(implementation: AppChaoxingStore): ChaoxingStore

    @Binds
    @Singleton
    abstract fun bindChaoxingReminders(
        implementation: AppChaoxingReminders
    ): ChaoxingReminders

    /**
     * 后台提醒只拿窄视图。合并超星与雨课堂的实现放在 :app，
     * 因此 :background 仍然只认识 ChaoxingReminderStore。
     */
    @Binds
    @Singleton
    abstract fun bindChaoxingReminderStore(
        implementation: AppReminderStore
    ): ChaoxingReminderStore

    companion object {

        /** 学习通的 ViewModel 只要求"一个 IO 调度器"，具体是谁由组合根决定。 */
        @Provides
        @XuexiaotongDispatcher
        fun provideXuexiaotongDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
