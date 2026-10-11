package com.lhy.yumerin.domain.background

interface BackgroundCompanionController {
    suspend fun setEnabled(enabled: Boolean)
    suspend fun stopAll()
    fun isEnabled(): Boolean
}
