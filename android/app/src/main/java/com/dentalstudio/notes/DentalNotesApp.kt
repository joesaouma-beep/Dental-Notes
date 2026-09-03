package com.dentalstudio.notes

import android.app.Application
import com.dentalstudio.notes.di.AppContainer

class DentalNotesApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
