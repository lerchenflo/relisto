package com.lerchenflo.relisto.datasource.network

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class RequestSpacingTest {

    private var now = 10_000L
    private val pauses = mutableListOf<Long>()
    private val spacing = RequestSpacing(minIntervalMillis = 300, nowMillis = { now }, pause = { pauses += it; now += it })

    @Test
    fun firstRequestStartsAtOnce() = runTest {
        spacing.awaitTurn()

        assertThat(pauses).isEmpty()
    }

    @Test
    fun nextRequestWaitsForTheRestOfTheInterval() = runTest {
        spacing.awaitTurn()
        now += 100
        spacing.awaitTurn()

        assertThat(pauses).containsExactly(200L)
    }

    @Test
    fun requestAfterTheIntervalStartsAtOnce() = runTest {
        spacing.awaitTurn()
        now += 500
        spacing.awaitTurn()

        assertThat(pauses).isEmpty()
    }
}
