package com.serendeep.marginalia

import android.app.Application
import com.serendeep.marginalia.search.TextIndexer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MarginaliaApp : Application() {
    @Inject lateinit var textIndexer: TextIndexer

    override fun onCreate() {
        super.onCreate()
        textIndexer.schedule()
    }
}
