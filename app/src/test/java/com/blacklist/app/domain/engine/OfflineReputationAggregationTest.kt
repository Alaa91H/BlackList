package com.blacklist.app.domain.engine

import com.blacklist.app.data.local.entity.OfflineReputationEntryEntity
import com.blacklist.app.data.local.entity.OfflineReputationSourceEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineReputationAggregationTest {
    @Test
    fun `aggregation keeps highest score and all declared provenance for exact number`() {
        val result = aggregateOfflineReputations(
            entries = listOf(
                OfflineReputationEntryEntity(sourceId = 2, normalizedNumber = "+4930123456", riskScore = 70, category = "marketing"),
                OfflineReputationEntryEntity(sourceId = 1, normalizedNumber = "+4930123456", riskScore = 90, category = "telemarketing"),
                OfflineReputationEntryEntity(sourceId = 1, normalizedNumber = "+4930123457", riskScore = 100, category = "fraud")
            ),
            sources = listOf(
                OfflineReputationSourceEntity(
                    id = 1,
                    sourceName = "Alpha",
                    sourceVersion = null,
                    sourceUrl = null,
                    fingerprintSha256 = "a".repeat(64),
                    entryCount = 2
                ),
                OfflineReputationSourceEntity(
                    id = 2,
                    sourceName = "Beta",
                    sourceVersion = null,
                    sourceUrl = null,
                    fingerprintSha256 = "b".repeat(64),
                    entryCount = 1
                )
            )
        )

        assertEquals(90, result.getValue("+4930123456").riskScore)
        assertEquals(listOf("Alpha", "Beta"), result.getValue("+4930123456").sources)
        assertEquals(listOf("marketing", "telemarketing"), result.getValue("+4930123456").categories)
        assertEquals(100, result.getValue("+4930123457").riskScore)
    }
}
