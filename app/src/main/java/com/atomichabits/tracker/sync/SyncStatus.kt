package com.atomichabits.tracker.sync

/** Technical state only, no habit names, diary content, emails or IDs of records. */
data class SyncStatus(
    val signedIn: Boolean = false,
    val checking: Boolean = false,
    val pending: Int = 0,
    val lastServerRead: Long? = null,
    val lastWriteAck: Long? = null,
    val error: String? = null
)
