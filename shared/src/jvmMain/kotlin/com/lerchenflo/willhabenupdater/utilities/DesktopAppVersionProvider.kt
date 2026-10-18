package com.lerchenflo.willhabenupdater.utilities

import com.lerchenflo.willhabenupdater.app.domain.AppVersionProvider

/** The installers' launcher sets "jpackage.app-version"; `gradlew run` builds have no version. */
class DesktopAppVersionProvider : AppVersionProvider {
    override val currentVersion: String? = System.getProperty("jpackage.app-version")
}
