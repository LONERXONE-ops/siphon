package com.lonerxone.siphon.data.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers extra request headers (Referer, cookies, user agent) for URLs found by the
 * in-app browser or the page scanner, so the download engines can send them later.
 */
object RequestHeaderRegistry {
    private val headers = ConcurrentHashMap<String, Map<String, String>>()
    private val directUrls = ConcurrentHashMap.newKeySet<String>()

    fun put(url: String, requestHeaders: Map<String, String>, isDirect: Boolean) {
        headers[url] = requestHeaders
        if (isDirect) directUrls.add(url)
    }

    fun get(url: String): Map<String, String>? = headers[url]

    fun isDirect(url: String): Boolean = directUrls.contains(url)
}