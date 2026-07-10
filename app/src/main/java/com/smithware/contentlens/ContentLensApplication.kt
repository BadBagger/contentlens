package com.smithware.contentlens

import android.app.Application
import com.smithware.contentlens.data.ContentLensDatabase
import com.smithware.contentlens.data.DemoSeedData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ContentLensApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            DemoSeedData.seedIfNeeded(ContentLensDatabase.get(this@ContentLensApplication).dao())
        }
    }
}
