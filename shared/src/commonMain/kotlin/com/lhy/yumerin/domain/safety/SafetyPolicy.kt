package com.lhy.yumerin.domain.safety

/** A small deterministic boundary that runs before a provider or memory writer. */
object SafetyPolicy {
    data class Decision(
        val allowed: Boolean,
        val reason: String? = null
    )

    private val minorSexualPatterns = listOf(
        "未成年.*性", "幼女.*性", "萝莉.*性", "小学生.*性", "未满十八.*性",
        "未成年.*裸", "幼女.*裸", "萝莉.*裸", "未成年.*色情"
    )
    private val harmfulOrIllegalPatterns = listOf(
        "怎么制作炸弹", "怎么做炸弹", "制造毒品", "怎么制毒", "怎么杀人",
        "如何杀人", "自杀方法", "自残方法", "如何入侵", "怎么盗号", "怎么诈骗",
        "制作枪支", "绕过警方", "躲避追查"
    )
    private val impersonationPatterns = listOf(
        "冒充真人", "冒充现实人物", "假装是.*本人", "伪装成.*本人", "冒充明星"
    )

    fun evaluate(input: String): Decision {
        val normalized = input.trim().lowercase()
        val pattern = (minorSexualPatterns + harmfulOrIllegalPatterns + impersonationPatterns)
            .firstOrNull { normalized.matches(Regex(".*$it.*")) }
        return if (pattern == null) {
            Decision(allowed = true)
        } else {
            Decision(allowed = false, reason = when {
                pattern in impersonationPatterns -> "现实人物冒充"
                pattern in harmfulOrIllegalPatterns -> "伤害或违法协助"
                else -> "未成年人性化内容"
            })
        }
    }

    fun refusal(characterName: String, reason: String): String =
        "我是$characterName，不能协助这类内容（$reason）。我们可以换成安全、虚构且尊重他人的方向，比如讨论情绪、创作设定或现实中的求助资源。"
}
