package io.github.androidpoet.kmpxmpp.omemopersistencesqlite

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.androidpoet.kmpxmpp.omemocore.OmemoDeviceBundle
import io.github.androidpoet.kmpxmpp.omemocore.OmemoIdentityTrust
import io.github.androidpoet.kmpxmpp.omemocore.OmemoSessionLifecycleState
import io.github.androidpoet.kmpxmpp.omemocore.OmemoSessionRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class SqliteOmemoSessionRepositoryTest {

    @Test
    fun test_saveLoadSessionAndTrust_updatesTrustState() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val repository = SqliteOmemoSessionRepository(driver)

        repository.saveBundle(
            OmemoDeviceBundle(
                userId = "bob",
                deviceId = 2002,
                identityKeyBase64 = "identity",
                preKeyBase64 = "pre",
                signedPreKeyBase64 = "signed-pre",
            ),
        )
        repository.saveSession(
            OmemoSessionRecord(
                userId = "bob",
                deviceId = 2002,
                rootKeyBase64 = "root",
                chainKeyBase64 = "chain",
                trust = OmemoIdentityTrust.Unknown,
            ),
        )

        repository.setTrust("bob", 2002, OmemoIdentityTrust.Trusted)
        val session = repository.findSession("bob", 2002)

        assertEquals(OmemoIdentityTrust.Trusted, session?.trust)
        assertEquals(4, readSchemaVersion(driver))
    }

    @Test
    fun test_deleteSession_removesStoredSession() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val repository = SqliteOmemoSessionRepository(driver)
        repository.saveSession(
            OmemoSessionRecord(
                userId = "bob",
                deviceId = 2002,
                rootKeyBase64 = "root",
                chainKeyBase64 = "chain",
                trust = OmemoIdentityTrust.Unknown,
            ),
        )
        repository.deleteSession("bob", 2002)
        assertNull(repository.findSession("bob", 2002))
        assertEquals(4, readSchemaVersion(driver))
    }

    @Test
    fun test_saveFindDeleteLifecycleState_roundTrips() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val repository = SqliteOmemoSessionRepository(driver)
        val state = OmemoSessionLifecycleState(
            userId = "bob",
            deviceId = 2002,
            establishedAtEpochMillis = 1_234_567L,
            operationCount = 9,
            nextSendMessageIndex = 15,
            highestReceivedMessageIndex = 12,
            receivedMessageIndexesWindow = setOf(4, 10, 12),
            sendChainKeyBase64 = "send-chain",
            receiveChainKeyBase64 = "receive-chain",
        )
        repository.saveLifecycleState(state)

        val loaded = repository.findLifecycleState("bob", 2002)
        assertEquals(state, loaded)

        repository.deleteLifecycleState("bob", 2002)
        assertNull(repository.findLifecycleState("bob", 2002))
    }

    @Test
    fun test_repositoryInit_whenSchemaV3_migratesToV4AndPreservesLifecycleRows() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(
            identifier = null,
            sql = "CREATE TABLE omemo_schema_metadata (key TEXT NOT NULL PRIMARY KEY, value INTEGER NOT NULL)",
            parameters = 0,
            binders = null,
        )
        driver.execute(
            identifier = null,
            sql = "INSERT INTO omemo_schema_metadata(key, value) VALUES ('schema_version', 3)",
            parameters = 0,
            binders = null,
        )
        driver.execute(
            identifier = null,
            sql = "CREATE TABLE omemo_lifecycle_state (" +
                "user_id TEXT NOT NULL," +
                "device_id INTEGER NOT NULL," +
                "established_at_ms INTEGER NOT NULL," +
                "operation_count INTEGER NOT NULL," +
                "next_send_index INTEGER NOT NULL," +
                "highest_receive_index INTEGER," +
                "receive_seen_window TEXT NOT NULL DEFAULT ''," +
                "PRIMARY KEY(user_id, device_id)" +
                ")",
            parameters = 0,
            binders = null,
        )
        driver.execute(
            identifier = null,
            sql = "INSERT INTO omemo_lifecycle_state(" +
                "user_id, device_id, established_at_ms, operation_count, next_send_index, highest_receive_index, receive_seen_window" +
                ") VALUES ('bob', 2002, 100, 2, 3, 1, '1,2,3')",
            parameters = 0,
            binders = null,
        )

        val repository = SqliteOmemoSessionRepository(driver)
        val migrated = repository.findLifecycleState("bob", 2002)

        assertEquals(4, readSchemaVersion(driver))
        assertEquals(100L, migrated?.establishedAtEpochMillis)
        assertEquals(2, migrated?.operationCount)
        assertEquals(3, migrated?.nextSendMessageIndex)
        assertEquals(1, migrated?.highestReceivedMessageIndex)
        assertEquals(setOf(1, 2, 3), migrated?.receivedMessageIndexesWindow)
        assertEquals(null, migrated?.sendChainKeyBase64)
        assertEquals(null, migrated?.receiveChainKeyBase64)
    }

    @Test
    fun test_findLifecycleState_whenReceiveWindowCorrupted_throws() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val repository = SqliteOmemoSessionRepository(driver)
        driver.execute(
            identifier = null,
            sql = "INSERT OR REPLACE INTO omemo_lifecycle_state(" +
                "user_id, device_id, established_at_ms, operation_count, next_send_index, highest_receive_index, receive_seen_window" +
                ") VALUES ('bob', 2002, 100, 2, 3, 1, '1,abc,3')",
            parameters = 0,
            binders = null,
        )

        assertFailsWith<IllegalStateException> {
            repository.findLifecycleState("bob", 2002)
        }
    }

    @Test
    fun test_findLifecycleState_whenSendChainKeyBlank_throws() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val repository = SqliteOmemoSessionRepository(driver)
        driver.execute(
            identifier = null,
            sql = "INSERT OR REPLACE INTO omemo_lifecycle_state(" +
                "user_id, device_id, established_at_ms, operation_count, next_send_index, highest_receive_index, receive_seen_window, send_chain_key_base64, receive_chain_key_base64" +
                ") VALUES ('bob', 2002, 100, 2, 3, 1, '1,2,3', '', 'ok')",
            parameters = 0,
            binders = null,
        )

        assertFailsWith<IllegalArgumentException> {
            repository.findLifecycleState("bob", 2002)
        }
    }

    @Test
    fun test_findLifecycleState_whenReceiveChainKeyBlank_throws() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val repository = SqliteOmemoSessionRepository(driver)
        driver.execute(
            identifier = null,
            sql = "INSERT OR REPLACE INTO omemo_lifecycle_state(" +
                "user_id, device_id, established_at_ms, operation_count, next_send_index, highest_receive_index, receive_seen_window, send_chain_key_base64, receive_chain_key_base64" +
                ") VALUES ('bob', 2002, 100, 2, 3, 1, '1,2,3', 'ok', '')",
            parameters = 0,
            binders = null,
        )

        assertFailsWith<IllegalArgumentException> {
            repository.findLifecycleState("bob", 2002)
        }
    }

    private fun readSchemaVersion(driver: JdbcSqliteDriver): Int =
        driver.executeQuery(
            identifier = null,
            sql = "SELECT value FROM omemo_schema_metadata WHERE key = 'schema_version'",
            mapper = { cursor: SqlCursor ->
                val hasValue = cursor.next().value
                val version = if (hasValue) cursor.getLong(0)?.toInt() ?: 0 else 0
                QueryResult.Value(version)
            },
            parameters = 0,
            binders = null,
        ).value
}
