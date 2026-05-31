package io.github.androidpoet.kmpxmpp.omemopersistencesqlite

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import io.github.androidpoet.kmpxmpp.omemocore.OmemoDeviceBundle
import io.github.androidpoet.kmpxmpp.omemocore.OmemoIdentityTrust
import io.github.androidpoet.kmpxmpp.omemocore.OmemoSessionLifecycleState
import io.github.androidpoet.kmpxmpp.omemocore.OmemoSessionRecord
import io.github.androidpoet.kmpxmpp.omemocore.OmemoSessionRepository

internal object ModuleMarker

public class SqliteOmemoSessionRepository(
    private val driver: SqlDriver,
) : OmemoSessionRepository {
    private val lock: Any = Any()

    init {
        applyMigrations()
    }

    override suspend fun saveBundle(bundle: OmemoDeviceBundle) {
        synchronized(lock) {
            driver.execute(
                identifier = null,
                sql = INSERT_OR_REPLACE_BUNDLE_SQL,
                parameters = 5,
            ) {
                bindString(0, bundle.userId)
                bindLong(1, bundle.deviceId.toLong())
                bindString(2, bundle.identityKeyBase64)
                bindString(3, bundle.preKeyBase64)
                bindString(4, bundle.signedPreKeyBase64)
            }
        }
    }

    override suspend fun findBundle(userId: String, deviceId: Int): OmemoDeviceBundle? = synchronized(lock) {
        driver.executeQuery(
            identifier = null,
            sql = FIND_BUNDLE_SQL,
            mapper = { cursor: SqlCursor -> QueryResult.Value(cursor.toBundle()) },
            parameters = 2,
        ) {
            bindString(0, userId)
            bindLong(1, deviceId.toLong())
        }.value
    }

    override suspend fun saveSession(session: OmemoSessionRecord) {
        synchronized(lock) {
            driver.execute(
                identifier = null,
                sql = INSERT_OR_REPLACE_SESSION_SQL,
                parameters = 5,
            ) {
                bindString(0, session.userId)
                bindLong(1, session.deviceId.toLong())
                bindString(2, session.rootKeyBase64)
                bindString(3, session.chainKeyBase64)
                bindLong(4, session.trust.toDbValue())
            }
        }
    }

    override suspend fun findSession(userId: String, deviceId: Int): OmemoSessionRecord? = synchronized(lock) {
        driver.executeQuery(
            identifier = null,
            sql = FIND_SESSION_SQL,
            mapper = { cursor: SqlCursor -> QueryResult.Value(cursor.toSession()) },
            parameters = 2,
        ) {
            bindString(0, userId)
            bindLong(1, deviceId.toLong())
        }.value
    }

    override suspend fun deleteSession(userId: String, deviceId: Int) {
        synchronized(lock) {
            driver.execute(
                identifier = null,
                sql = DELETE_SESSION_SQL,
                parameters = 2,
            ) {
                bindString(0, userId)
                bindLong(1, deviceId.toLong())
            }
        }
    }

    override suspend fun setTrust(userId: String, deviceId: Int, trust: OmemoIdentityTrust) {
        synchronized(lock) {
            driver.execute(
                identifier = null,
                sql = UPDATE_TRUST_SQL,
                parameters = 3,
            ) {
                bindLong(0, trust.toDbValue())
                bindString(1, userId)
                bindLong(2, deviceId.toLong())
            }
        }
    }

    override suspend fun saveLifecycleState(state: OmemoSessionLifecycleState) {
        synchronized(lock) {
            driver.execute(
                identifier = null,
                sql = INSERT_OR_REPLACE_LIFECYCLE_SQL,
                parameters = 9,
            ) {
                bindString(0, state.userId)
                bindLong(1, state.deviceId.toLong())
                bindLong(2, state.establishedAtEpochMillis)
                bindLong(3, state.operationCount.toLong())
                bindLong(4, state.nextSendMessageIndex.toLong())
                val highestReceivedIndex = state.highestReceivedMessageIndex
                if (highestReceivedIndex == null) bindString(5, null) else bindLong(5, highestReceivedIndex.toLong())
                bindString(6, encodeReceiveWindow(state.receivedMessageIndexesWindow))
                bindString(7, state.sendChainKeyBase64)
                bindString(8, state.receiveChainKeyBase64)
            }
        }
    }

    override suspend fun findLifecycleState(userId: String, deviceId: Int): OmemoSessionLifecycleState? = synchronized(lock) {
        driver.executeQuery(
            identifier = null,
            sql = FIND_LIFECYCLE_SQL,
            mapper = { cursor: SqlCursor -> QueryResult.Value(cursor.toLifecycleState()) },
            parameters = 2,
        ) {
            bindString(0, userId)
            bindLong(1, deviceId.toLong())
        }.value
    }

    override suspend fun deleteLifecycleState(userId: String, deviceId: Int) {
        synchronized(lock) {
            driver.execute(
                identifier = null,
                sql = DELETE_LIFECYCLE_SQL,
                parameters = 2,
            ) {
                bindString(0, userId)
                bindLong(1, deviceId.toLong())
            }
        }
    }

    private fun applyMigrations() {
        ensureSchemaMetadataTable()
        val version = readSchemaVersion()
        migrateToCurrentSchema(version)
    }

    private fun ensureSchemaMetadataTable() {
        driver.execute(
            identifier = null,
            sql = CREATE_SCHEMA_METADATA_TABLE_SQL,
            parameters = 0,
            binders = null,
        )
        driver.execute(
            identifier = null,
            sql = INSERT_DEFAULT_SCHEMA_VERSION_SQL,
            parameters = 0,
            binders = null,
        )
    }

    private fun readSchemaVersion(): Int =
        driver.executeQuery(
            identifier = null,
            sql = READ_SCHEMA_VERSION_SQL,
            mapper = { cursor: SqlCursor ->
                val hasValue = cursor.next().value
                val version = if (hasValue) cursor.getLong(0)?.toInt() ?: 0 else 0
                QueryResult.Value(version)
            },
            parameters = 0,
            binders = null,
        ).value

    private fun migrateToCurrentSchema(currentVersion: Int) {
        if (currentVersion < 1) {
            driver.execute(
                identifier = null,
                sql = CREATE_BUNDLES_TABLE_SQL,
                parameters = 0,
                binders = null,
            )
            driver.execute(
                identifier = null,
                sql = CREATE_SESSIONS_TABLE_SQL,
                parameters = 0,
                binders = null,
            )
        }
        if (currentVersion < 2) {
            driver.execute(
                identifier = null,
                sql = CREATE_LIFECYCLE_TABLE_SQL,
                parameters = 0,
                binders = null,
            )
        }
        if (currentVersion in 2..2) {
            driver.execute(
                identifier = null,
                sql = ADD_LIFECYCLE_RECEIVE_WINDOW_COLUMN_SQL,
                parameters = 0,
                binders = null,
            )
        }
        if (currentVersion in 3..3) {
            driver.execute(
                identifier = null,
                sql = ADD_LIFECYCLE_SEND_CHAIN_KEY_COLUMN_SQL,
                parameters = 0,
                binders = null,
            )
            driver.execute(
                identifier = null,
                sql = ADD_LIFECYCLE_RECEIVE_CHAIN_KEY_COLUMN_SQL,
                parameters = 0,
                binders = null,
            )
        }
        driver.execute(
            identifier = null,
            sql = UPDATE_SCHEMA_VERSION_SQL,
            parameters = 1,
        ) {
            bindLong(0, CURRENT_SCHEMA_VERSION.toLong())
        }
    }

    private fun SqlCursor.toBundle(): OmemoDeviceBundle? {
        if (!next().value) return null
        val userId = getString(0) ?: return null
        val deviceId = getLong(1)?.toInt() ?: return null
        val identityKeyBase64 = getString(2) ?: return null
        val preKeyBase64 = getString(3) ?: return null
        val signedPreKeyBase64 = getString(4) ?: return null
        return OmemoDeviceBundle(
            userId = userId,
            deviceId = deviceId,
            identityKeyBase64 = identityKeyBase64,
            preKeyBase64 = preKeyBase64,
            signedPreKeyBase64 = signedPreKeyBase64,
        )
    }

    private fun SqlCursor.toSession(): OmemoSessionRecord? {
        if (!next().value) return null
        val userId = getString(0) ?: return null
        val deviceId = getLong(1)?.toInt() ?: return null
        val rootKeyBase64 = getString(2) ?: return null
        val chainKeyBase64 = getString(3) ?: return null
        val trustValue = getLong(4)?.toInt() ?: return null
        return OmemoSessionRecord(
            userId = userId,
            deviceId = deviceId,
            rootKeyBase64 = rootKeyBase64,
            chainKeyBase64 = chainKeyBase64,
            trust = trustValue.toTrust(),
        )
    }

    private fun SqlCursor.toLifecycleState(): OmemoSessionLifecycleState? {
        if (!next().value) return null
        val userId = getString(0) ?: return null
        val deviceId = getLong(1)?.toInt() ?: return null
        val establishedAtMillis = getLong(2) ?: return null
        val operationCount = getLong(3)?.toInt() ?: return null
        val nextSendIndex = getLong(4)?.toInt() ?: return null
        val highestReceivedIndex = getLong(5)?.toInt()
        val sendChainKeyBase64 = getString(7)
        val receiveChainKeyBase64 = getString(8)
        require(sendChainKeyBase64 == null || sendChainKeyBase64.isNotBlank()) {
            "Corrupted OMEMO lifecycle send chain key for $userId/$deviceId."
        }
        require(receiveChainKeyBase64 == null || receiveChainKeyBase64.isNotBlank()) {
            "Corrupted OMEMO lifecycle receive chain key for $userId/$deviceId."
        }
        return OmemoSessionLifecycleState(
            userId = userId,
            deviceId = deviceId,
            establishedAtEpochMillis = establishedAtMillis,
            operationCount = operationCount,
            nextSendMessageIndex = nextSendIndex,
            highestReceivedMessageIndex = highestReceivedIndex,
            receivedMessageIndexesWindow = decodeReceiveWindow(getString(6)),
            sendChainKeyBase64 = sendChainKeyBase64,
            receiveChainKeyBase64 = receiveChainKeyBase64,
        )
    }

    private fun OmemoIdentityTrust.toDbValue(): Long =
        when (this) {
            OmemoIdentityTrust.Unknown -> 0L
            OmemoIdentityTrust.Trusted -> 1L
            OmemoIdentityTrust.Revoked -> 2L
        }

    private fun Int.toTrust(): OmemoIdentityTrust =
        when (this) {
            1 -> OmemoIdentityTrust.Trusted
            2 -> OmemoIdentityTrust.Revoked
            else -> OmemoIdentityTrust.Unknown
        }

    private companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 4

        const val CREATE_SCHEMA_METADATA_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS omemo_schema_metadata (" +
                "key TEXT NOT NULL PRIMARY KEY," +
                "value INTEGER NOT NULL" +
                ")"
        const val INSERT_DEFAULT_SCHEMA_VERSION_SQL =
            "INSERT OR IGNORE INTO omemo_schema_metadata(key, value) VALUES ('schema_version', 0)"
        const val READ_SCHEMA_VERSION_SQL =
            "SELECT value FROM omemo_schema_metadata WHERE key = 'schema_version'"
        const val UPDATE_SCHEMA_VERSION_SQL =
            "UPDATE omemo_schema_metadata SET value = ? WHERE key = 'schema_version'"

        const val CREATE_BUNDLES_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS omemo_bundles (" +
                "user_id TEXT NOT NULL," +
                "device_id INTEGER NOT NULL," +
                "identity_key_base64 TEXT NOT NULL," +
                "pre_key_base64 TEXT NOT NULL," +
                "signed_pre_key_base64 TEXT NOT NULL," +
                "PRIMARY KEY(user_id, device_id)" +
                ")"

        const val CREATE_SESSIONS_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS omemo_sessions (" +
                "user_id TEXT NOT NULL," +
                "device_id INTEGER NOT NULL," +
                "root_key_base64 TEXT NOT NULL," +
                "chain_key_base64 TEXT NOT NULL," +
                "trust_state INTEGER NOT NULL," +
                "PRIMARY KEY(user_id, device_id)" +
                ")"
        const val CREATE_LIFECYCLE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS omemo_lifecycle_state (" +
                "user_id TEXT NOT NULL," +
                "device_id INTEGER NOT NULL," +
                "established_at_ms INTEGER NOT NULL," +
                "operation_count INTEGER NOT NULL," +
                "next_send_index INTEGER NOT NULL," +
                "highest_receive_index INTEGER," +
                "receive_seen_window TEXT NOT NULL DEFAULT ''," +
                "send_chain_key_base64 TEXT," +
                "receive_chain_key_base64 TEXT," +
                "PRIMARY KEY(user_id, device_id)" +
                ")"
        const val ADD_LIFECYCLE_RECEIVE_WINDOW_COLUMN_SQL =
            "ALTER TABLE omemo_lifecycle_state ADD COLUMN receive_seen_window TEXT NOT NULL DEFAULT ''"
        const val ADD_LIFECYCLE_SEND_CHAIN_KEY_COLUMN_SQL =
            "ALTER TABLE omemo_lifecycle_state ADD COLUMN send_chain_key_base64 TEXT"
        const val ADD_LIFECYCLE_RECEIVE_CHAIN_KEY_COLUMN_SQL =
            "ALTER TABLE omemo_lifecycle_state ADD COLUMN receive_chain_key_base64 TEXT"

        const val INSERT_OR_REPLACE_BUNDLE_SQL =
            "INSERT OR REPLACE INTO omemo_bundles(" +
                "user_id, device_id, identity_key_base64, pre_key_base64, signed_pre_key_base64" +
                ") VALUES (?, ?, ?, ?, ?)"

        const val FIND_BUNDLE_SQL =
            "SELECT user_id, device_id, identity_key_base64, pre_key_base64, signed_pre_key_base64 " +
                "FROM omemo_bundles WHERE user_id = ? AND device_id = ?"

        const val INSERT_OR_REPLACE_SESSION_SQL =
            "INSERT OR REPLACE INTO omemo_sessions(" +
                "user_id, device_id, root_key_base64, chain_key_base64, trust_state" +
                ") VALUES (?, ?, ?, ?, ?)"

        const val FIND_SESSION_SQL =
            "SELECT user_id, device_id, root_key_base64, chain_key_base64, trust_state " +
                "FROM omemo_sessions WHERE user_id = ? AND device_id = ?"

        const val DELETE_SESSION_SQL = "DELETE FROM omemo_sessions WHERE user_id = ? AND device_id = ?"
        const val UPDATE_TRUST_SQL = "UPDATE omemo_sessions SET trust_state = ? WHERE user_id = ? AND device_id = ?"
        const val INSERT_OR_REPLACE_LIFECYCLE_SQL =
            "INSERT OR REPLACE INTO omemo_lifecycle_state(" +
                "user_id, device_id, established_at_ms, operation_count, next_send_index, highest_receive_index, receive_seen_window, send_chain_key_base64, receive_chain_key_base64" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        const val FIND_LIFECYCLE_SQL =
            "SELECT user_id, device_id, established_at_ms, operation_count, next_send_index, highest_receive_index, receive_seen_window, send_chain_key_base64, receive_chain_key_base64 " +
                "FROM omemo_lifecycle_state WHERE user_id = ? AND device_id = ?"
        const val DELETE_LIFECYCLE_SQL = "DELETE FROM omemo_lifecycle_state WHERE user_id = ? AND device_id = ?"
    }

    private fun encodeReceiveWindow(values: Set<Int>): String =
        values.sorted().joinToString(",")

    private fun decodeReceiveWindow(encoded: String?): Set<Int> {
        if (encoded.isNullOrBlank()) return emptySet()
        return encoded.split(',').map { token ->
            val trimmed = token.trim()
            val parsed = trimmed.toIntOrNull()
                ?: throw IllegalStateException("Corrupted OMEMO lifecycle receive window token: '$trimmed'.")
            require(parsed > 0) {
                "Corrupted OMEMO lifecycle receive window value: '$parsed'."
            }
            parsed
        }.toSet()
    }
}
