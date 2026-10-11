package com.lhy.yumerin.domain.memory

import com.lhy.yumerin.core.model.MemoryCategory

data class MemoryCandidate(
    val category: MemoryCategory,
    val content: String,
    val confidence: Float,
    val sensitive: Boolean = false,
    val reviewReason: String? = null
)

/** Conservative V1 extractor: only explicit, stable preference/profile statements become memory. */
object MemoryPolicy {
    private val preferencePattern = Regex("^(我喜欢|我爱|我偏好)([^，。！？!?]{1,24})[。！!！]?$" )
    private val dislikePattern = Regex("^(我不喜欢|我讨厌)([^，。！？!?]{1,24})[。！!！]?$" )
    private val namePattern = Regex("^(我叫|我的名字是)([^，。！？!?]{1,16})[。！!！]?$" )
    private val uncertainPreferencePattern = Regex("^(我可能喜欢|我好像喜欢)([^，。！？!?]{1,24})[。！!！]?$" )
    private val relationshipPattern = Regex("^(我们是|你是我的|我们之间是)([^，。！？!?]{1,24})[。！!！]?$" )
    private val eventPattern = Regex("^(我的生日是|纪念日是|我毕业于|我入职于)([^，。！？!?]{1,32})[。！!！]?$" )
    private val sensitiveWords = listOf("密码", "身份证", "银行卡", "住址", "地址", "电话", "疾病", "病史", "创伤")

    fun extract(text: String): List<MemoryCandidate> {
        val normalized = text.trim()
        preferencePattern.matchEntire(normalized)?.let { match ->
            return listOf(candidate(MemoryCategory.USER_PREFERENCE, "用户喜欢${match.groupValues[2]}", 0.92f, normalized))
        }
        dislikePattern.matchEntire(normalized)?.let { match ->
            return listOf(candidate(MemoryCategory.USER_PREFERENCE, "用户不喜欢${match.groupValues[2]}", 0.92f, normalized))
        }
        namePattern.matchEntire(normalized)?.let { match ->
            return listOf(candidate(MemoryCategory.LONG_TERM, "用户的名字是${match.groupValues[2]}", 0.98f, normalized))
        }
        relationshipPattern.matchEntire(normalized)?.let { match ->
            return listOf(candidate(MemoryCategory.RELATIONSHIP, "用户与角色的关系是${match.groupValues[2]}", 0.9f, normalized))
        }
        eventPattern.matchEntire(normalized)?.let { match ->
            return listOf(candidate(MemoryCategory.IMPORTANT_EVENT, "重要事件：${match.groupValues[1]}${match.groupValues[2]}", 0.88f, normalized))
        }
        uncertainPreferencePattern.matchEntire(normalized)?.let { match ->
            return listOf(
                MemoryCandidate(
                    MemoryCategory.USER_PREFERENCE,
                    "用户可能喜欢${match.groupValues[2]}",
                    0.58f,
                    sensitive = false,
                    reviewReason = "表述包含不确定性，需要用户确认"
                )
            )
        }
        return emptyList()
    }

    private fun candidate(category: MemoryCategory, content: String, confidence: Float, source: String): MemoryCandidate {
        val sensitive = sensitiveWords.any(source::contains)
        return MemoryCandidate(
            category = category,
            content = content,
            confidence = if (sensitive) confidence.coerceAtMost(0.45f) else confidence,
            sensitive = sensitive,
            reviewReason = if (sensitive) "内容可能包含敏感个人信息，需要用户确认" else null
        )
    }
}
