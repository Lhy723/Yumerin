package com.lhy.yumerin.domain.usecase

import kotlin.time.Clock

internal fun useCaseNowMillis(): Long = Clock.System.now().toEpochMilliseconds()
