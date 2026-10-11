package com.lhy.yumerin.data.room

import com.lhy.yumerin.core.model.*

internal inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    runCatching { enumValueOf<T>(value) }.getOrDefault(default)
