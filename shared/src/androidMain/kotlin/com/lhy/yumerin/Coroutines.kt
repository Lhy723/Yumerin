package com.lhy.yumerin

actual suspend fun awaitFrame() {
    kotlinx.coroutines.android.awaitFrame()
}
