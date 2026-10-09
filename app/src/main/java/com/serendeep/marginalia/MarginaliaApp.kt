package com.serendeep.marginalia

import android.app.Application
import com.serendeep.marginalia.handwriting.InkIndexer
import com.serendeep.marginalia.search.TextIndexer
import com.serendeep.marginalia.reminder.ReminderScheduler
import com.serendeep.marginalia.update.UpdateManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MarginaliaApp : Application() {
    @Inject lateinit var textIndexer: TextIndexer
    @Inject lateinit var inkIndexer: InkIndexer
    @Inject lateinit var updates: UpdateManager

    override fun onCreate() {
        super.onCreate()
        textIndexer.schedule()
        inkIndexer.schedule()
        updates.start()
        CoroutineScope(Dispatchers.IO).launch { ReminderScheduler.arm(this@MarginaliaApp) }
    }
}
