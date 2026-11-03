package com.ahu.ahutong.data.rainclass

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

@OptIn(ExperimentalSerializationApi::class)
val RainClassJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * 服务端偶尔把数字写成字符串。宽松解析避免一个字段让整次同步失败。
 */
object LenientLongSerializer : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("LenientLong", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeLong()
        val element = jsonDecoder.decodeJsonElement()
        val primitive = element as? JsonPrimitive ?: return 0L
        return primitive.longOrNull ?: primitive.contentOrNull?.toLongOrNull() ?: 0L
    }

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

@Serializable
data class CourseListResponse(
    val success: Boolean? = null,
    @Serializable(with = LenientLongSerializer::class)
    val errcode: Long = 0,
    val errmsg: String = "",
    val msg: String = "",
    val data: CourseListData? = null
)

@Serializable
data class CourseListData(
    val list: List<CourseNode> = emptyList()
)

@Serializable
data class CourseNode(
    @SerialName("classroom_id")
    @Serializable(with = LenientLongSerializer::class)
    val classroomId: Long = 0,
    @Serializable(with = LenientLongSerializer::class)
    val term: Long = 0,
    val name: String = "",
    val course: CourseData? = null,
    val teacher: TeacherData? = null
)

@Serializable
data class CourseData(
    @Serializable(with = LenientLongSerializer::class)
    val id: Long = 0,
    val name: String = ""
)

@Serializable
data class TeacherData(
    val name: String = ""
)

@Serializable
data class ChapterResponse(
    val success: Boolean? = null,
    @Serializable(with = LenientLongSerializer::class)
    val errcode: Long = 0,
    val errmsg: String = "",
    val msg: String = "",
    val data: ChapterData? = null
)

@Serializable
data class ChapterData(
    @SerialName("course_id")
    @Serializable(with = LenientLongSerializer::class)
    val courseId: Long = 0,
    @SerialName("course_name")
    val courseName: String = "",
    @SerialName("course_chapter")
    val chapters: List<ChapterNode> = emptyList()
)

@Serializable
data class ChapterNode(
    @Serializable(with = LenientLongSerializer::class)
    val id: Long = 0,
    val name: String = "",
    @SerialName("section_leaf_list")
    val leaves: List<LeafNode> = emptyList()
)

@Serializable
data class LeafNode(
    @Serializable(with = LenientLongSerializer::class)
    val id: Long = 0,
    @SerialName("leafinfo_id")
    @Serializable(with = LenientLongSerializer::class)
    val leafInfoId: Long = 0,
    val name: String = "",
    @SerialName("leaf_type")
    @Serializable(with = LenientLongSerializer::class)
    val leafType: Long = 0,
    @SerialName("start_time")
    @Serializable(with = LenientLongSerializer::class)
    val startTime: Long = 0,
    @SerialName("end_time")
    @Serializable(with = LenientLongSerializer::class)
    val endTime: Long = 0,
    @SerialName("score_deadline")
    @Serializable(with = LenientLongSerializer::class)
    val scoreDeadline: Long = 0,
    @SerialName("is_show")
    val isShow: Boolean? = null,
    @SerialName("is_locked")
    val isLocked: Boolean = false,
    @SerialName("is_assessed")
    val isAssessed: Boolean = false,
    @SerialName("is_score")
    val isScore: Boolean = false,
    @SerialName("is_open_type")
    val isOpenType: Boolean = false,
    @SerialName("chapter_id")
    @Serializable(with = LenientLongSerializer::class)
    val chapterId: Long = 0,
    @SerialName("section_id")
    val sectionId: JsonElement? = null
)

@Serializable
data class LeafDetailResponse(
    val success: Boolean? = null,
    val msg: String = "",
    val data: LeafDetailData? = null
)

@Serializable
data class LeafDetailData(
    val name: String = "",
    @SerialName("content_info")
    val contentInfo: ContentInfo? = null
)

@Serializable
data class ContentInfo(
    val status: JsonElement? = null
)

@Serializable
data class OAuthResponse(
    @Serializable(with = LenientLongSerializer::class)
    val code: Long = 0,
    val msg: String = "",
    val data: OAuthData? = null
)

@Serializable
data class OAuthData(
    val appId: String = "",
    val state: String = "",
    val redirectUri: String = ""
)
