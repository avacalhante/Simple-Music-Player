package com.simplemobiletools.musicplayer.analytics

import java.util.concurrent.Executor
import java.util.concurrent.Executors

object AnalyticsExecutor {
    private val executor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AnalyticsExecutor")
    }

    val myExecutor: Executor get() = executor
}
