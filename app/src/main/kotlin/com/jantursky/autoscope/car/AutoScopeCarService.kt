package com.jantursky.autoscope.car

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jantursky.autoscope.AppContainer
import com.jantursky.autoscope.R
import com.jantursky.autoscope.container

/** Entry point for Android Auto. Declared in the manifest with the POI category. */
class AutoScopeCarService : CarAppService() {

    // Release builds accept only the Android Auto host (see res/values/hosts_allowlist.xml).
    // Debuggable builds accept any host so other DHU or host builds can bind during development.
    override fun createHostValidator(): HostValidator {
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        return if (debuggable) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext).addAllowedHosts(R.array.hosts_allowlist).build()
        }
    }

    override fun onCreateSession(): Session = AutoScopeSession(container)
}

/**
 * Binds the car sources to this Session's CarContext, holds the refresh ticker and registers the
 * hardware listeners while it lives. Everything is undone on ON_DESTROY.
 */
class AutoScopeSession(private val container: AppContainer) : Session() {

    private val sources = container.carSources
    private var hardware: HardwareListeners? = null

    init {
        sources.performance.sessionCreated()
        lifecycle.addObserver(LifecycleLogger(container.eventLog, "Session"))
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) release()
        })
    }

    // The API level and host info are known only from here on (CarContext.getCarAppApiLevel javadoc).
    override fun onCreateScreen(intent: Intent): Screen {
        val ports = AndroidCarPorts.create(carContext)
        sources.bind(ports)
        val listeners = HardwareListeners(
            carContext, ports.apiLevel, ports.permissions, sources.hardware, sources.samples, sources.telemetryState,
            container.eventLog,
        )
        hardware = listeners
        listeners.start()
        container.acquireTicker()
        val icons = CarView.TABS.associateWith { Templates.letterIcon(it.title.first()) }
        return RootScreen(carContext, SessionDeps(container, ports, listeners, icons))
    }

    override fun onNewIntent(intent: Intent) {
        container.eventLog.add("Session onNewIntent ${intent.action}")
    }

    override fun onCarConfigurationChanged(newConfiguration: Configuration) {
        container.eventLog.add(
            "Car configuration changed: ${newConfiguration.screenWidthDp}x${newConfiguration.screenHeightDp} dp, " +
                "${newConfiguration.densityDpi} dpi, dark mode ${carContext.isDarkMode}, " +
                "font scale ${newConfiguration.fontScale}, locale ${newConfiguration.locales.toLanguageTags()}",
        )
        sources.refreshConstraints()
    }

    private fun release() {
        hardware?.stop()
        if (hardware != null) container.releaseTicker()
        hardware = null
        sources.unbind()
    }
}
