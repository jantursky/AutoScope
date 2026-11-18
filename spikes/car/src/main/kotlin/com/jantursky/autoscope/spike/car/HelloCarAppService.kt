package com.jantursky.autoscope.spike.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator

class HelloCarAppService : CarAppService() {

    // Spike only: accept any host so the DHU and unsigned builds can bind.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = HelloScreen(carContext)
    }
}

private class HelloScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle("hello").build())
            .addRow(Row.Builder().setTitle("Car App API level: ${carContext.carAppApiLevel}").build())
            .build()
        return PaneTemplate.Builder(pane).setTitle("AutoScope spike A").build()
    }
}
