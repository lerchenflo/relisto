package com.lerchenflo.relisto.marketplace.domain

/** Marketplaces a listing can be published on. More follow in Phase 8. */
enum class Marketplace {
    WILLHABEN
}

/** Sync state of one listing on one marketplace. */
enum class SyncState {
    /** Never published on this marketplace. */
    LOCAL_ONLY,
    /** Local content equals the last snapshot from the marketplace. */
    SYNCED,
    /** Edited locally after the last sync. */
    LOCAL_CHANGES,
    /** Changed on the marketplace, no local edits. */
    REMOTE_CHANGED,
    /** Changed on both sides. */
    CONFLICT,
    /** Publish started, can be resumed. */
    PUBLISHING,
    /** Last sync step failed. */
    ERROR,
    /** Not on the marketplace any more (deleted there, or replaced by the draft of a renew). */
    REMOTE_GONE
}
