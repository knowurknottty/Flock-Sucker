package com.inversionlabs.flocksucker

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.inversionlabs.flocksucker.worker.NukeWorker
import dagger.hilt.android.testing.HiltTestApplication

/** Custom Hilt runner that restores production WorkManager initialization in tests. */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)

    override fun onStart() {
        val configuration = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(InstrumentationWorkerFactory())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(targetContext, configuration)
        super.onStart()
    }
}

private class InstrumentationWorkerFactory : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? = if (workerClassName == NukeWorker::class.java.name) {
        InertNukeWorker(appContext, workerParameters)
    } else null
}

/** Instrumentation must never execute a destructive wipe against the shared test device. */
private class InertNukeWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : Worker(appContext, workerParameters) {
    override fun doWork(): Result = Result.success()
}
