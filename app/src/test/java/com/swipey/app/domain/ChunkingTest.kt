package com.swipey.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChunkingTest {
    @Test fun emptyListProducesNoChunks() =
        assertEquals(emptyList<List<Int>>(), emptyList<Int>().chunkedForRequest())

    @Test fun singleItemProducesOneChunk() =
        assertEquals(1, listOf(1).chunkedForRequest().size)

    @Test fun exactlyMaxProducesOneChunk() =
        assertEquals(1, (1..500).toList().chunkedForRequest().size)

    @Test fun oneOverMaxProducesTwoChunks() {
        val chunks = (1..501).toList().chunkedForRequest()
        assertEquals(2, chunks.size)
        assertEquals(500, chunks[0].size)
        assertEquals(1, chunks[1].size)
    }

    @Test fun thousandProducesTwoFullChunks() {
        val chunks = (1..1000).toList().chunkedForRequest()
        assertEquals(2, chunks.size)
        assertEquals(500, chunks[1].size)
    }

    @Test fun chunkingLosesNothing() {
        val input = (1..1234).toList()
        assertEquals(input, input.chunkedForRequest().flatten())
    }

    @Test fun maxIsFiveHundred() = assertEquals(500, MAX_URIS_PER_REQUEST)
}
