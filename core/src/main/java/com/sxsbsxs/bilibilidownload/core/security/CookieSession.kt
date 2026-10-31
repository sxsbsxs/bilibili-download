package com.sxsbsxs.bilibilidownload.core.security

/**
 * Cookie is intentionally memory-only in this milestone. Do not persist raw SESSDATA in preferences/logs.
 * A future official app authorization flow can provide a short-lived session here.
 */
class CookieSession {
    @Volatile private var value: String? = null
    fun set(cookie: String?) { value = cookie?.trim()?.takeIf { it.isNotEmpty() } }
    fun get(): String? = value
    fun clear() { value = null }
    val isLoggedIn: Boolean get() = !value.isNullOrBlank()
}
