package com.sarvesh173.talos

import android.app.Application
import com.sarvesh173.talos.ui.logs.CrashLogger

/**
 * Installs the crash handler.
 *
 * On the Application, not MainActivity: a crash during startup, in a service, or
 * in the accessibility bridge would otherwise never be recorded. The handler is
 * installed before anything else can run.
 */
class TalosApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }
}
