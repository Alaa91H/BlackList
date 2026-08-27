package com.blacklist.app.domain.engine

import com.blacklist.app.data.local.dao.CallerReputationDao
import com.blacklist.app.data.local.entity.CallerReputationEntity
import com.blacklist.app.domain.model.UserVerdict
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReputationEngineTest {
    @Test
    fun `not spam verdict is durable and explicit`() = runBlocking {
        val dao = InMemoryCallerReputationDao()
        val engine = ReputationEngine(dao)

        engine.setUserVerdict("+4930123456", UserVerdict.NOT_SPAM)

        val saved = dao.find("+4930123456")
        assertNotNull(saved)
        assertEquals(UserVerdict.NOT_SPAM.name, saved?.userVerdict)
        assertEquals("NEUTRAL", saved?.level)
        assertEquals(10, saved?.spamScore)
    }

    private class InMemoryCallerReputationDao : CallerReputationDao {
        private val values = linkedMapOf<String, CallerReputationEntity>()

        override suspend fun find(normalized: String): CallerReputationEntity? = values[normalized]

        override fun observeAll(): Flow<List<CallerReputationEntity>> = flowOf(values.values.toList())

        override suspend fun topRisk(): List<CallerReputationEntity> =
            values.values.sortedByDescending { it.riskScore }.take(50)

        override suspend fun getAll(): List<CallerReputationEntity> = values.values.toList()

        override suspend fun upsert(entity: CallerReputationEntity) {
            values[entity.normalizedNumber] = entity
        }

        override suspend fun incrementBlocked(normalized: String, now: Long) {
            val current = values[normalized] ?: return
            values[normalized] = current.copy(
                blockedCalls = current.blockedCalls + 1,
                totalCalls = current.totalCalls + 1,
                lastSeen = now
            )
        }

        override suspend fun incrementAllowed(normalized: String, now: Long) {
            val current = values[normalized] ?: return
            values[normalized] = current.copy(
                allowedCalls = current.allowedCalls + 1,
                totalCalls = current.totalCalls + 1,
                lastSeen = now
            )
        }

        override suspend fun delete(normalized: String) {
            values.remove(normalized)
        }
    }
}
