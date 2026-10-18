package com.lerchenflo.willhabenupdater.utilities

import android.content.Context
import com.lerchenflo.willhabenupdater.app.domain.AppVersionProvider

class AndroidAppVersionProvider(context: Context) : AppVersionProvider {
    override val currentVersion: String? =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
}
