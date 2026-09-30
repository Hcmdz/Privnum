package com.hcmdz.privnum

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class AwaitReadyTest {

    @Test
    fun `emission counts as ready`() = runBlocking {
        assertTrue(awaitReady(flowOf(listOf("a"))))
    }

    @Test
    fun `immediate failure counts as ready`() = runBlocking {
        assertTrue(awaitReady(flow<List<String>> { throw IllegalStateException("boom") }))
    }
}
