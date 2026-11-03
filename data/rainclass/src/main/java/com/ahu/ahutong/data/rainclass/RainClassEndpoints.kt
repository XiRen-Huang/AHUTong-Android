package com.ahu.ahutong.data.rainclass

/**
 * 雨课堂主站只读接口与节流参数。URL、请求头、轮询参数只在这里改。
 *
 * 这里刻意不出现考试域地址：本版本只读主站。
 */
object RainClassEndpoints {
    const val HOST = "https://changjiang.yuketang.cn"

    /** 登录第 1 步：拿 csrftoken。 */
    const val CSRF_PRIMER = "$HOST/v/course_meta/user_info"

    /** 登录第 2 步：拿 appId/state。 */
    const val WECHAT_AUTH_PARAM = "$HOST/api/v3/user/login/wechat-auth-param"

    /** 登录第 3/4/5 步。 */
    const val WECHAT_QR_CONNECT = "https://open.weixin.qq.com/connect/qrconnect"
    const val WECHAT_QR_IMAGE = "https://open.weixin.qq.com/connect/qrcode"
    const val WECHAT_QR_POLL = "https://lp.open.weixin.qq.com/connect/l/qrconnect"

    /** 登录第 6 步：302 即成功。 */
    const val WECHAT_CALLBACK = "$HOST/api/v3/user/login/wechat-web-callback"

    /** 课程列表。 */
    const val COURSES = "$HOST/v2/api/web/courses/list?identity=2"

    /** 课程章节树。 */
    const val CHAPTER = "$HOST/mooc-api/v1/lms/learn/course/chapter"

    /** 叶子详情（只读 status）。 */
    const val LEAF_INFO = "$HOST/mooc-api/v1/lms/learn/leaf_info"

    const val HEADER_XTBZ = "xtbz"
    const val HEADER_CLASSROOM_ID = "classroom-id"
    const val XTBZ_MAIN = "ykt"

    const val QR_POLL_INTERVAL_MS = 2_000L
    /** 微信二维码本身约 5 分钟有效；本地超时与之对齐，过期仍以服务端返回为准。 */
    const val QR_TIMEOUT_MS = 300_000L
    const val COURSE_THROTTLE_MS = 250L
    const val STATUS_THROTTLE_MS = 200L
    const val STATUS_BUDGET = 50
    const val STATUS_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
}
