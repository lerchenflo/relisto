package com.lerchenflo.willhabenupdater.app.domain

/** A browser cookie of a logged-in session. */
data class SessionCookie(
    val name: String,
    val value: String,
    /** Without leading dot. */
    val domain: String,
    /** True when the cookie belongs to exactly [domain], false when subdomains get it too. */
    val hostOnly: Boolean,
    val path: String = "/",
    val secure: Boolean = true,
    val httpOnly: Boolean = false,
    /** Null for session cookies. */
    val expiresAt: Long? = null,
)
