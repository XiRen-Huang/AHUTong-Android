plugins {
    // 版本由根 build.gradle.kts 的插件别名统一提供。
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/*
 * 雨课堂只读数据层：扫码登录、课程/章节同步、本地缓存。
 *
 * 只访问 changjiang.yuketang.cn 主站的只读接口；不包含任何考试域、交卷、答题调用。
 * OkHttpClient 由 :app 的 RainClassWiringModule 用 AhuHttp.plain(...) 构造后注入，
 * 因此这里不依赖 :core:network。依赖方向由 R31 看守。
 */
android {
    namespace = "com.ahu.ahutong.data.rainclass"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":core:auth")) // SecureStorage
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
}
