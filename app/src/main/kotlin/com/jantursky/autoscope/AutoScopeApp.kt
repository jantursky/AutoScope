package com.jantursky.autoscope

import android.app.Application

class AutoScopeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val android.content.Context.container: AppContainer
    get() = (applicationContext as AutoScopeApp).container
