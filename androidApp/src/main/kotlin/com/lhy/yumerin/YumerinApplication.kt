package com.lhy.yumerin

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import com.lhy.yumerin.data.room.createAndroidAppGraph
import com.lhy.yumerin.data.background.NotificationChannels
import com.lhy.yumerin.data.local.LocalAiService
import com.lhy.yumerin.domain.AppGraph
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class YumerinApplication : Application(), ComponentCallbacks2 {
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensure(this)
        startKoin {
            androidContext(this@YumerinApplication)
            modules(
                module {
                    single<AppGraph> { createAndroidAppGraph(get()) }
                    single { get<AppGraph>().useCases }
                }
            )
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            LocalAiService.getInstance().trimMemory()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    override fun onLowMemory() {
        LocalAiService.getInstance().trimMemory()
    }
}
