package com.lhy.yumerin.domain.agent

import com.lhy.yumerin.core.model.AgentTrigger
import com.lhy.yumerin.core.model.EntityId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes runs per Chat; scheduled triggers are skipped instead of delaying behind user work. */
class AgentAdmissionArbiter {
    private class ChatLock {
        val mutex = Mutex()
        var users: Int = 0
    }

    private val registryLock = Mutex()
    private val chatLocks = mutableMapOf<EntityId, ChatLock>()

    suspend fun <T> withAdmission(
        chatId: EntityId,
        trigger: AgentTrigger,
        action: suspend () -> T
    ): T? {
        val chatLock = registryLock.withLock {
            chatLocks.getOrPut(chatId) { ChatLock() }.also { it.users += 1 }
        }
        var acquired = false
        try {
            acquired = if (trigger.isScheduled) {
                chatLock.mutex.tryLock()
            } else {
                chatLock.mutex.lock()
                true
            }
            if (!acquired) return null
            return action()
        } finally {
            if (acquired) chatLock.mutex.unlock()
            registryLock.withLock {
                chatLock.users -= 1
                if (chatLock.users == 0 && chatLocks[chatId] === chatLock) chatLocks.remove(chatId)
            }
        }
    }

    private val AgentTrigger.isScheduled: Boolean
        get() = this == AgentTrigger.SCHEDULED_PROACTIVE || this == AgentTrigger.SCHEDULED_GOAL || this == AgentTrigger.DELEGATED_GOAL
}
