package com.carlog

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.carlog.data.diagnostics.CrashReporter
import com.carlog.data.diagnostics.DiagnosticArea
import com.carlog.data.diagnostics.DiagnosticLog
import com.carlog.data.diagnostics.DiagnosticSeverity
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CarLogApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        // Журнал поднимаем первым: он должен успеть записать падение,
        // случившееся на старте, до всего остального
        DiagnosticLog.init(this)
        CrashReporter.install()
        DiagnosticLog.record(
            area = DiagnosticArea.APP,
            severity = DiagnosticSeverity.INFO,
            event = "app_started"
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
