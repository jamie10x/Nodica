package com.jamie.nodica.di

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class KoinApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Add a small delay BEFORE starting Koin
        Thread.sleep(500) // Delay for 500ms - Adjust if needed
        startKoin {
            androidContext(this@KoinApp)
            modules(appModule)
        }
    }
}
