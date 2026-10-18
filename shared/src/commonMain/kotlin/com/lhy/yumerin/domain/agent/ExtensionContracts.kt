package com.lhy.yumerin.domain.agent

import com.lhy.yumerin.core.model.AgentCapabilityDescriptor

/**
 * Execution gateway for out-of-runtime tools. V1 capability modules execute
 * in-process; this contract reserves the seam where external tool workers
 * (for example an MCP-shaped bridge) attach without changing the registry,
 * the runtime loop, or the approval model.
 */
interface ToolGateway {
    /** Describes a tool in JSON-Schema form, compatible with MCP tool metadata. */
    fun describe(): AgentCapabilityDescriptor

    /** Executes one call and returns a bounded observation. */
    suspend fun call(argumentsJson: String): AgentCapabilityResult
}

/**
 * Prompt lifecycle hooks registered at composition time. Hooks observe or
 * rewrite prompt text; they never grant capabilities or bypass safety checks.
 */
interface PromptHook {
    val name: String

    /** Called before the prompt is assembled; returns text to append or null. */
    suspend fun beforePrompt(context: HookContext): String?

    /** Called after a successful final reply. */
    suspend fun afterResponse(context: HookContext, replyText: String) {}
}

class HookContext(
    val chatId: com.lhy.yumerin.core.model.EntityId,
    val characterId: com.lhy.yumerin.core.model.EntityId?,
    val traceId: com.lhy.yumerin.core.model.EntityId
)

/**
 * A registered extension's self-declaration. In-process registration only in
 * V1: dynamic loading is deferred and never implied. Extension manifests
 * cannot alter tool permissions — approval stays with the user (ADR-0022).
 */
data class AgentExtensionManifest(
    val name: String,
    val version: String,
    /** Capabilities this extension claims to provide, e.g. "tool:calculator". */
    val providedCapabilities: List<String> = emptyList(),
    /** Permissions the extension requests; ungranted ones gate its tools. */
    val requestedPermissions: List<String> = emptyList()
)
