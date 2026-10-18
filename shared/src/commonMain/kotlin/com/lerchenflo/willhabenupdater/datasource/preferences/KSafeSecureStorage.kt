package com.lerchenflo.willhabenupdater.datasource.preferences

import eu.anifantakis.lib.ksafe.KSafe

/** [SecureStorage] backed by KSafe (encrypted with an OS-protected key). */
class KSafeSecureStorage(
    private val ksafe: KSafe,
) : SecureStorage {

    override suspend fun getString(key: String): String? = ksafe.get(key, "").ifEmpty { null }

    override suspend fun putString(key: String, value: String) {
        ksafe.put(key, value)
    }

    override suspend fun delete(key: String) {
        ksafe.delete(key)
    }
}
