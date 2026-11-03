package com.ahu.ahutong.di

import android.content.Context
import com.ahu.ahutong.data.network.AhuHttp
import com.ahu.ahutong.data.rainclass.HttpRainClassApi
import com.ahu.ahutong.data.rainclass.RainClassApi
import com.ahu.ahutong.data.rainclass.RainClassCache
import com.ahu.ahutong.data.rainclass.RainClassCookieJar
import com.ahu.ahutong.data.rainclass.RainClassLocalStore
import com.ahu.ahutong.data.rainclass.RainClassQrLogin
import com.ahu.ahutong.data.rainclass.RainClassRepository
import com.ahu.ahutong.data.rainclass.RainClassSession
import com.ahu.ahutong.data.rainclass.RainClassStore
import com.ahu.ahutong.data.rainclass.SecureRainClassCookiePersistence
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * 雨课堂的接线：客户端只在组合根构造，数据模块拿现成的 [OkHttpClient]。
 *
 * 登录必须关闭自动重定向：第 6 步回调靠 302 作为成功信号。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RainClassWiringModule {

    @Binds
    @Singleton
    abstract fun bindRainClassSession(implementation: RainClassRepository): RainClassSession

    @Binds
    @Singleton
    abstract fun bindRainClassStore(implementation: RainClassLocalStore): RainClassStore

    @Binds
    @Singleton
    abstract fun bindRainClassCache(implementation: RainClassLocalStore): RainClassCache

    companion object {

        @Provides
        @Singleton
        fun provideRainClassCookieJar(): RainClassCookieJar =
            RainClassCookieJar(SecureRainClassCookiePersistence())

        @Provides
        @Singleton
        fun provideRainClassClient(cookieJar: RainClassCookieJar): OkHttpClient =
            AhuHttp.plain(
                connectTimeoutSeconds = 15,
                readTimeoutSeconds = 20,
                followRedirects = false,
                followSslRedirects = false
            ).cookieJar(cookieJar).build()

        @Provides
        @Singleton
        fun provideRainClassApi(client: OkHttpClient): RainClassApi =
            HttpRainClassApi(client)

        @Provides
        @Singleton
        fun provideRainClassQrLogin(client: OkHttpClient): RainClassQrLogin =
            RainClassQrLogin(client)

        @Provides
        @Singleton
        fun provideRainClassLocalStore(
            @ApplicationContext context: Context
        ): RainClassLocalStore = RainClassLocalStore(context)

        @Provides
        @Singleton
        fun provideRainClassRepository(
            api: RainClassApi,
            qrLogin: RainClassQrLogin,
            cookieJar: RainClassCookieJar,
            cache: RainClassCache
        ): RainClassRepository = RainClassRepository(api, qrLogin, cookieJar, cache)
    }
}
