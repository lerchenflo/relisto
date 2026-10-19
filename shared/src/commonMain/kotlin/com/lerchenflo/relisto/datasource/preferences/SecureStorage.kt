package com.lerchenflo.relisto.datasource.preferences

/** Encrypted key-value storage for secrets such as session cookies. */
interface SecureStorage {
    suspend fun getString(key: String): String?
    suspend fun putString(key: String, value: String)
    suspend fun delete(key: String)
}
