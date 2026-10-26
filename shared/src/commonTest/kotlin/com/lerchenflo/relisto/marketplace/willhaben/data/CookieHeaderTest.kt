package com.lerchenflo.relisto.marketplace.willhaben.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.lerchenflo.relisto.app.domain.SessionCookie
import kotlin.test.Test

class CookieHeaderTest {

    @Test
    fun cookieHeaderBecomesHostCookies() {
        val cookies = parseCookieHeader("x-bbx-csrf-token=abc; SESSION=s1=2==; empty=", "www.willhaben.at")

        assertThat(cookies).containsExactly(
            SessionCookie(name = "x-bbx-csrf-token", value = "abc", domain = "www.willhaben.at", hostOnly = true),
            SessionCookie(name = "SESSION", value = "s1=2==", domain = "www.willhaben.at", hostOnly = true),
            SessionCookie(name = "empty", value = "", domain = "www.willhaben.at", hostOnly = true),
        )
    }

    @Test
    fun noCookiesOrBrokenPartsGiveNothing() {
        assertThat(parseCookieHeader(null, "www.willhaben.at")).isEmpty()
        assertThat(parseCookieHeader("  ;novalue; =x", "www.willhaben.at")).isEmpty()
    }
}
