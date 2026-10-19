package com.lerchenflo.relisto.utilities

import com.lerchenflo.relisto.app.domain.AppVersionProvider

/** The installers' launcher sets "jpackage.app-version"; `gradlew run` builds have no version. */
class DesktopAppVersionProvider : AppVersionProvider {
    override val currentVersion: String? = System.getProperty("jpackage.app-version")
}
