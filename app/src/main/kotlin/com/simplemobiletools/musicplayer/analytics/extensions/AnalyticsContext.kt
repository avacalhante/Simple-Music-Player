package com.simplemobiletools.musicplayer.analytics.extensions

import android.content.Context
import com.simplemobiletools.musicplayer.analytics.ListeningAnalytics
import com.simplemobiletools.musicplayer.analytics.ListeningAnalyticsStore

val Context.analytics: ListeningAnalyticsStore
    get() = ListeningAnalytics.getInstance(applicationContext)
