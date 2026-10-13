package com.lhy.yumerin.domain.agent

import com.lhy.yumerin.core.model.AgentCapabilityDescriptor
import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.MessageSenderType
import com.lhy.yumerin.domain.repository.CompanionScheduleRepository
import kotlinx.coroutines.flow.first

/** Read-only view of schedules owned by the current character. */
class ScheduleLookupCapability(
    private val schedules: CompanionScheduleRepository
) : AgentCapability {
    override val descriptor = AgentCapabilityDescriptor(
        name = "schedule_overview",
        description = "List this character's scheduled reminders and their daily times when the user asks about plans or reminders.",
        parametersJsonSchema = """{"type":"object","properties":{},"additionalProperties":false}"""
    )

    override fun isAvailableFor(request: AgentRequest): Boolean {
        val text = request.proactivePrompt ?: request.messages.lastOrNull { it.senderType == MessageSenderType.USER }?.text.orEmpty()
        return SCHEDULE_INTENT.containsMatchIn(text)
    }

    override suspend fun execute(
        argumentsJson: String,
        context: AgentCapabilityContext
    ): AgentCapabilityResult {
        if (argumentsJson.trim() != "{}") return AgentCapabilityResult(false, "Invalid arguments: this capability takes no parameters.")
        val characterId = context.characterId
            ?: return AgentCapabilityResult(false, "There is no active character in this run.")
        val items = schedules.observeSchedules().first()
            .filter { it.characterId == characterId && it.deletedAt == null }
            .sortedWith(compareByDescending<com.lhy.yumerin.core.model.CompanionSchedule> { it.enabled }.thenBy { it.hour }.thenBy { it.minute })
            .take(MAX_SCHEDULES)
        if (items.isEmpty()) return AgentCapabilityResult(true, "This character has no saved schedules.")
        val observation = items.joinToString("\n") {
            val hour = it.hour.toString().padStart(2, '0')
            val minute = it.minute.toString().padStart(2, '0')
            "${it.title.take(120)} — daily at $hour:$minute — ${if (it.enabled) "enabled" else "paused"}"
        }
        return AgentCapabilityResult(true, observation, "已读取该角色的计划")
    }

    private companion object {
        const val MAX_SCHEDULES = 12
        val SCHEDULE_INTENT = Regex("提醒|计划|安排|预约|日程|schedule|remind|plan", RegexOption.IGNORE_CASE)
    }
}
