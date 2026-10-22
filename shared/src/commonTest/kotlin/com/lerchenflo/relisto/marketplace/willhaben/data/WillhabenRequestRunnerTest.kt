package com.lerchenflo.relisto.marketplace.willhaben.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.lerchenflo.relisto.app.domain.DataError
import com.lerchenflo.relisto.app.domain.Result
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class WillhabenRequestRunnerTest {

    private var renewCalls = 0
    private var renewSucceeds = true
    private val waits = mutableListOf<Long>()

    private val runner = WillhabenRequestRunner(
        renewSession = {
            renewCalls++
            renewSucceeds
        },
        backoff = { millis -> waits += millis },
    )

    @Test
    fun successIsReturnedAsIs() = runTest {
        val result = runner.run { Result.Success("ok") }

        assertThat(result).isEqualTo(Result.Success("ok"))
        assertThat(renewCalls).isEqualTo(0)
    }

    @Test
    fun expiredSessionIsRenewedAndRequestRepeatedOnce() = runTest {
        val responses = mutableListOf<Result<String, DataError.Network>>(
            Result.Error(DataError.Network.UNAUTHORIZED),
            Result.Success("ok"),
        )

        val result = runner.run { responses.removeAt(0) }

        assertThat(result).isEqualTo(Result.Success("ok"))
        assertThat(renewCalls).isEqualTo(1)
    }

    @Test
    fun failedRenewalReportsUnauthorized() = runTest {
        renewSucceeds = false
        var attempts = 0

        val result = runner.run<String> {
            attempts++
            Result.Error(DataError.Network.UNAUTHORIZED)
        }

        assertThat(result).isEqualTo(Result.Error(DataError.Network.UNAUTHORIZED))
        assertThat(attempts).isEqualTo(1)
    }

    @Test
    fun rateLimitIsRetriedWithGrowingWaits() = runTest {
        val responses = mutableListOf<Result<String, DataError.Network>>(
            Result.Error(DataError.Network.TOO_MANY_REQUESTS),
            Result.Error(DataError.Network.SERVICE_UNAVAILABLE),
            Result.Success("ok"),
        )

        val result = runner.run { responses.removeAt(0) }

        assertThat(result).isEqualTo(Result.Success("ok"))
        assertThat(waits).containsExactly(2_000L, 4_000L)
    }

    @Test
    fun rateLimitGivesUpAfterThreeRetries() = runTest {
        var attempts = 0

        val result = runner.run<String> {
            attempts++
            Result.Error(DataError.Network.TOO_MANY_REQUESTS)
        }

        assertThat(result).isEqualTo(Result.Error(DataError.Network.TOO_MANY_REQUESTS))
        assertThat(attempts).isEqualTo(4)
    }

    @Test
    fun requestsRunOneAfterAnother() = runTest {
        var running = 0
        var maxRunning = 0

        (1..3).map {
            async {
                runner.run {
                    running++
                    maxRunning = maxOf(maxRunning, running)
                    delay(100)
                    running--
                    Result.Success(it)
                }
            }
        }.awaitAll()

        assertThat(maxRunning).isEqualTo(1)
    }

    @Test
    fun atMostFourUploadsRunAtTheSameTime() = runTest {
        var running = 0
        var mostAtOnce = 0
        val uploads = (1..6).map {
            async {
                runner.runParallel {
                    running++
                    mostAtOnce = maxOf(mostAtOnce, running)
                    delay(100)
                    running--
                    Result.Success(it)
                }
            }
        }

        assertThat(uploads.awaitAll()).containsExactly(*(1..6).map { Result.Success(it) }.toTypedArray())
        assertThat(mostAtOnce).isEqualTo(4)
    }

    @Test
    fun uploadsRenewAnExpiredSessionToo() = runTest {
        val responses = mutableListOf<Result<String, DataError.Network>>(
            Result.Error(DataError.Network.UNAUTHORIZED),
            Result.Success("ok"),
        )

        assertThat(runner.runParallel { responses.removeAt(0) }).isEqualTo(Result.Success("ok"))
        assertThat(renewCalls).isEqualTo(1)
    }
}
