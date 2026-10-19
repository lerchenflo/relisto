package com.lerchenflo.relisto.utilities

import android.content.Context
import com.lerchenflo.relisto.app.domain.AppVersionProvider

class AndroidAppVersionProvider(context: Context) : AppVersionProvider {
    override val currentVersion: String? =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
}
