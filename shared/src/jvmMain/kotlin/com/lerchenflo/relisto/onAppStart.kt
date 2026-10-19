package com.lerchenflo.relisto

import com.lerchenflo.relisto.di.datasourceModule
import com.lerchenflo.relisto.di.desktopPlatformModule
import com.lerchenflo.relisto.di.presentationModule
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform

/** Called once from the desktop `main()` before the first window opens. */
fun onAppStart() {
    if (KoinPlatform.getKoinOrNull() == null) {
        startKoin {
            modules(desktopPlatformModule, datasourceModule, presentationModule)
        }
    }
}
