package com.lhy.yumerin

actual suspend fun awaitFrame() {
    kotlinx.coroutines.delay(1000L / 60L)
}
