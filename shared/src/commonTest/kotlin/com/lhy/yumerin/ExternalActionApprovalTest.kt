package com.lhy.yumerin

import com.lhy.yumerin.core.model.AgentCapabilityCall
import com.lhy.yumerin.core.model.AgentCapabilityDescriptor
import com.lhy.yumerin.core.model.AgentCapabilityEffect
import com.lhy.yumerin.core.model.AgentConversationMessage
import com.lhy.yumerin.core.model.AgentModelStep
import com.lhy.yumerin.core.model.AgentReply
import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.AgentService
import com.lhy.yumerin.core.model.AgentTrigger
import com.lhy.yumerin.core.model.ChatKind
import com.lhy.yumerin.core.model.ChatMessage
import com.lhy.yumerin.core.model.ChatThread
import com.lhy.yumerin.core.model.CharacterProfile
import com.lhy.yumerin.core.model.ExternalConnectionStatus
import com.lhy.yumerin.core.model.ExternalServiceConnection
import com.lhy.yumerin.core.model.ExternalServiceKind
import com.lhy.yumerin.core.model.GenerationParameters
import com.lhy.yumerin.core.model.MessageSenderType
import com.lhy.yumerin.core.model.MessageType
import com.lhy.yumerin.core.model.ModelProfile
import com.lhy.yumerin.core.model.ModelProviderKind
import com.lhy.yumerin.core.model.PendingExternalActionRules
import com.lhy.yumerin.core.model.PendingExternalActionStatus
import com.lhy.yumerin.core.model.SummaryMaintenanceMode
import com.lhy.yumerin.data.memory.InMemoryExternalAuthorizationRepository
import com.lhy.yumerin.data.memory.InMemoryPendingExternalActionRepository
import com.lhy.yumerin.domain.agent.AgentCapabilityContext
import com.lhy.yumerin.domain.agent.AgentRuntime
import com.lhy.yumerin.domain.agent.ApprovedExternalActionScope
import com.lhy.yumerin.domain.agent.ExternalActionHandling
import com.lhy.yumerin.domain.agent.ExternalActionRoute
import com.lhy.yumerin.domain.agent.ExternalActionScope
import com.lhy.yumerin.domain.agent.ExternalRunContext
import com.lhy.yumerin.domain.agent.ExternalWriteCapability
import com.lhy.yumerin.domain.agent.ExternalWriteOutcome
import com.lhy.yumerin.domain.repository.PendingExternalActionRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalActionApprovalTest {

    @Test
    fun writeWithoutConnectionIsRejectedAndNeverExecutes() = runTest {
        val harness = harness(connected = false, enabled = false)

        harness.runtime.run(request(), AgentTrigger.USER_MESSAGE)

        assertEquals(0, harness.connector.executedScopes.size)
        assertTrue(harness.pending.observeActions().value.isEmpty())
    }

    @Test
    fun connectedButDisabledCapabilityStaysUnavailable() = runTest {
        val harness = harness(connected = true, enabled = false)

        harness.runtime.run(request(), AgentTrigger.USER_MESSAGE)

        assertEquals(0, harness.connector.executedScopes.size)
        assertTrue(harness.connector.preparedScopes.isEmpty())
    }

    @Test
    fun backgroundTriggerPersistsPendingActionAndNeverExecutes() = runTest {
        val harness = harness(connected = true, enabled = true)

        val reply = harness.runtime.run(request(), AgentTrigger.DELEGATED_GOAL)
        assertEquals(listOf("post_update"), harness.gateCalls, "reply=" + reply.text)
        val saved = harness.pending.observeActions().value
        assertEquals(1, saved.size)
        assertEquals("post_update", saved.single().actionType)
        assertEquals("完整内容", saved.single().content)
        assertEquals(
            PendingExternalActionRules.TTL_MILLIS,
            saved.single().expiresAt - saved.single().createdAt
        )
        assertEquals(
            PendingExternalActionStatus.EXPIRED,
            PendingExternalActionRules.effectiveStatus(saved.single(), saved.single().expiresAt + 1)
        )
    }

    @Test
    fun hardBlockedActionTypeNeverProducesAPreview() = runTest {
        val harness = harness(connected = true, enabled = true, actionType = "permanent_deletion")

        harness.runtime.run(request(), AgentTrigger.DELEGATED_GOAL)

        assertTrue(harness.connector.preparedScopes.isEmpty())
        assertEquals(0, harness.connector.executedScopes.size)
    }

    @Test
    fun approvingGateExecutesOnceAndUncertainOutcomeIsNotRetried() = runTest {
        val harness = harness(connected = true, enabled = true, uncertain = true)
        val approving = ApprovingGate(harness.connector)
        harness.runtime.externalActionGate = approving

        harness.runtime.run(request(), AgentTrigger.DELEGATED_GOAL)

        assertEquals(1, harness.connector.executedScopes.size)
        assertEquals(1, approving.executionCount)
        // Uncertain outcome: one status check, never a second attempt.
        assertEquals(1, harness.connector.statusChecks)
    }

    // -- fixtures --

    private class Harness(
        val runtime: AgentRuntime,
        val connector: FakeConnector,
        val pending: InMemoryPendingExternalActionRepository,
        val gateCalls: MutableList<String>
    )

    private fun harness(
        connected: Boolean,
        enabled: Boolean,
        actionType: String = "post_update",
        uncertain: Boolean = false
    ): Harness {
        val authorizations = InMemoryExternalAuthorizationRepository()
        if (connected) {
            kotlinx.coroutines.runBlocking {
                authorizations.saveConnection(
                    ExternalServiceConnection(
                        id = "connection-1",
                        service = ExternalServiceKind.GENERIC_PROVIDER,
                        accountLabel = "user@example.com",
                        readScopes = setOf("read"),
                        writeScopes = setOf("write"),
                        enabledCapabilities = if (enabled) setOf("external_post") else emptySet(),
                        status = ExternalConnectionStatus.CONNECTED,
                        createdAt = 0L,
                        updatedAt = 0L
                    )
                )
            }
        }
        val connector = FakeConnector(actionType, uncertain)
        val capability = ExternalPostCapability("external_post", actionType, connector)
        val pending = InMemoryPendingExternalActionRepository()
        val gateCalls = mutableListOf<String>()
        val runtime = AgentRuntime(ScriptedService(), listOf(capability)).apply {
            externalAuthorization = object : com.lhy.yumerin.domain.agent.ExternalAuthorizationAccess {
                override suspend fun getConnection(service: ExternalServiceKind) =
                    authorizations.getConnections().firstOrNull {
                        it.service == service && it.status == ExternalConnectionStatus.CONNECTED
                    }

                override suspend fun accountLabel(service: ExternalServiceKind) =
                    authorizations.getConnections().firstOrNull { it.service == service }?.accountLabel
            }
            externalActionGate = RecordingGate(
                com.lhy.yumerin.domain.agent.NoInteractiveExternalActionGate(pending) { 1_000_000L }, gateCalls
            )
        }
        return Harness(runtime, connector, pending, gateCalls)
    }

    private fun request(): AgentRequest {
        val message = ChatMessage(
            id = "m1", chatId = chat.id, senderId = null, senderType = MessageSenderType.USER,
            senderName = "我", senderAvatarUri = null, text = "整理结果发到主页", timestamp = 1L,
            type = MessageType.TEXT, isProactive = false, isRead = true
        )
        return AgentRequest(chat = chat, character = character, messages = listOf(message), memories = emptyList(), model = model)
    }

    private class RecordingGate(private val inner: com.lhy.yumerin.domain.agent.ExternalActionGate, private val log: MutableList<String>) : com.lhy.yumerin.domain.agent.ExternalActionGate {
        override fun routeFor(trigger: AgentTrigger) = inner.routeFor(trigger)
        override suspend fun handle(
            scope: ExternalActionScope,
            capability: ExternalWriteCapability,
            context: AgentCapabilityContext,
            trigger: AgentTrigger,
            runContext: ExternalRunContext
        ): ExternalActionHandling {
            log += scope.actionType
            return inner.handle(scope, capability, context, trigger, runContext)
        }
    }

    private class ScriptedService : AgentService {
        private var step = 0

        override suspend fun reply(request: AgentRequest): AgentReply = AgentReply("好的。", request.model.id)

        override suspend fun nextStep(
            request: AgentRequest,
            conversation: List<AgentConversationMessage>,
            capabilities: List<AgentCapabilityDescriptor>,
            onAccumulatedText: suspend (String) -> Unit
        ): AgentModelStep {
            step += 1
            return if (step == 1 && capabilities.isNotEmpty()) {
                AgentModelStep.CapabilityRequests(
                    calls = listOf(AgentCapabilityCall("call-1", capabilities.first().name, "{}"))
                )
            } else {
                val toolMessage = conversation.lastOrNull { it.role == "tool" }?.content ?: "no-tool-message"
                AgentModelStep.FinalResponse(AgentReply(toolMessage, request.model.id))
            }
        }
    }

    private class ApprovingGate(private val connector: FakeConnector) : com.lhy.yumerin.domain.agent.ExternalActionGate {
        var executionCount = 0

        override fun routeFor(trigger: AgentTrigger) = ExternalActionRoute.BACKGROUND_PERSIST

        override suspend fun handle(
            scope: ExternalActionScope,
            capability: ExternalWriteCapability,
            context: AgentCapabilityContext,
            trigger: AgentTrigger,
            runContext: ExternalRunContext
        ): ExternalActionHandling {
            executionCount += 1
            return when (val outcome = connector.execute(scope)) {
                is ExternalWriteOutcome.Succeeded ->
                    ExternalActionHandling.Executed(outcome.observation, com.lhy.yumerin.core.model.ExternalActionPhase.SUCCEEDED)
                is ExternalWriteOutcome.Failed ->
                    ExternalActionHandling.Executed(outcome.observation, com.lhy.yumerin.core.model.ExternalActionPhase.FAILED)
                is ExternalWriteOutcome.Uncertain ->
                    ExternalActionHandling.Executed(outcome.observation, com.lhy.yumerin.core.model.ExternalActionPhase.UNCERTAIN)
            }
        }
    }

    private class FakeConnector(private val actionType: String, private val uncertain: Boolean) {
        val preparedScopes = mutableListOf<ExternalActionScope>()
        val executedScopes = mutableListOf<ExternalActionScope>()
        var statusChecks = 0

        fun prepare(): ExternalActionScope? {
            if (actionType == "permanent_deletion") return null
            preparedScopes += ExternalActionScope(
                serviceKind = ExternalServiceKind.GENERIC_PROVIDER,
                capabilityName = "external_post",
                actionType = actionType,
                accountLabel = "user@example.com",
                target = "主页",
                content = "完整内容"
            )
            return preparedScopes.last()
        }

        fun execute(scope: ExternalActionScope): ExternalWriteOutcome {
            executedScopes += scope
            statusChecks += 1
            return if (uncertain) {
                ExternalWriteOutcome.Uncertain("服务没有确认是否成功")
            } else {
                ExternalWriteOutcome.Succeeded("已发布")
            }
        }
    }

    private class ExternalPostCapability(
        private val capabilityName: String,
        private val actionType: String,
        private val connector: FakeConnector
    ) : ExternalWriteCapability {
        override val serviceKind = ExternalServiceKind.GENERIC_PROVIDER
        override val requiredWriteScopes = setOf("write")

        override val descriptor = AgentCapabilityDescriptor(
            name = capabilityName,
            description = "Posts externally.",
            parametersJsonSchema = """{"type":"object"}""",
            effect = AgentCapabilityEffect.EXTERNAL_SIDE_EFFECT
        )

        override suspend fun prepareScope(argumentsJson: String, context: AgentCapabilityContext): ExternalActionScope? {
            if (argumentsJson.contains("bad")) return null
            return connector.prepare()
        }

        override suspend fun executeWrite(
            approved: ApprovedExternalActionScope,
            context: AgentCapabilityContext
        ): ExternalWriteOutcome = connector.execute(approved.scope)

        // External writes never run through the plain capability path (ADR 0022).
        override suspend fun execute(argumentsJson: String, context: AgentCapabilityContext): com.lhy.yumerin.domain.agent.AgentCapabilityResult =
            com.lhy.yumerin.domain.agent.AgentCapabilityResult(false, "External writes require per-action approval.")
    }

    private companion object {
        val character = CharacterProfile(
            id = "ext-character", name = "小雨", avatarUri = null,
            relationshipFraming = "朋友", setting = "测试", description = "",
            personality = "温柔", tone = "自然", backstory = "", interactionStyle = "简短",
            modelProfileId = "model-test", archived = false, createdAt = 0L, updatedAt = 0L
        )
        val chat = ChatThread(
            id = "ext-chat", kind = ChatKind.DIRECT, title = character.name,
            characterId = character.id, groupId = null, avatarUri = null,
            lastMessagePreview = "", lastActivityAt = 0L, unreadCount = 0,
            pinned = false, muted = false, shortTermSummary = "",
            summaryMaintenanceMode = SummaryMaintenanceMode.AUTO
        )
        val model = ModelProfile(
            id = "model-test", displayName = "测试模型", providerKind = ModelProviderKind.OPENAI_COMPATIBLE,
            providerName = "test", baseUrl = null, modelId = "test", credentialAlias = null,
            contextLength = 8_192,
            contextLengthMode = com.lhy.yumerin.core.model.ContextLengthMode.KNOWN,
            generationParameters = GenerationParameters(), localModelId = null, localModelVersion = null,
            enabled = true, createdAt = 0L, updatedAt = 0L
        )
    }
}
