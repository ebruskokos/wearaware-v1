# Pair, Learn, and Train My Glasses — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the shallow "quick-learn from capture" system with a full pairing and GATT-based learning pipeline that builds a `KnownTargetSignature` from CompanionDeviceManager association + BLE advertising + GATT service discovery, with richer soft-scoring and a dedicated Pair & Learn UI flow.

**Architecture:** A new `PairAndLearnViewModel` orchestrates CDM association → GATT discovery → signature building, emitting `Channel<PairAndLearnEffect>` side effects for the Screen to handle the CDM picker dialog. Domain models (`KnownTargetSignature`, `KnownTargetMatchResult`) fully replace the old `LearnedDeviceSignature`/`LearnedMatchResult` types — all consuming code is migrated in this plan. BLE/GATT details stay in the data layer; scoring logic lives in domain use cases.

**Tech Stack:** Kotlin coroutines (`suspendCancellableCoroutine` GATT bridge), CompanionDeviceManager (API 26+), Room 2.6.1 (schema v5→6), SharedPreferences + Gson, MockK + `StandardTestDispatcher`, Hilt, Compose Navigation.

---

## File Structure

### New files
| File | Responsibility |
|------|---------------|
| `domain/model/KnownTargetSignature.kt` | Replaces `LearnedDeviceSignature`; includes GATT UUIDs + behavior profile |
| `domain/model/KnownTargetMatchInput.kt` | Replaces `LearnedMatchInput`; adds `gattServiceUuids`, `connectable` |
| `domain/model/KnownTargetMatchResult.kt` | Replaces `LearnedMatchResult`; uses `KnownMatchConfidence` (STRONG/POSSIBLE/WEAK/NONE) |
| `domain/model/GattDiscoveryResult.kt` | GATT service + characteristic UUIDs from a single connection |
| `domain/model/PairedLearningSession.kt` | Persisted session record; includes `LearningSessionStatus` enum |
| `domain/model/PairedLearningEvent.kt` | Individual event within a session; includes `LearningEventType` enum (13 types) |
| `domain/repository/KnownTargetRepository.kt` | Replaces `LearnedSignatureRepository` — load/save/clear |
| `domain/repository/LearningSessionRepository.kt` | CRUD for sessions and events |
| `data/repository/KnownTargetRepositoryImpl.kt` | SharedPreferences + Gson; same key pattern as old impl |
| `data/local/LearningSessionEntity.kt` | Room entity: `learning_session` table |
| `data/local/LearningEventEntity.kt` | Room entity: `learning_event` table |
| `data/local/LearningSessionDao.kt` | Room DAO for sessions + events |
| `data/repository/LearningSessionRepositoryImpl.kt` | Room-backed impl |
| `data/ble/BleGattManager.kt` | `connectAndDiscover()` — GATT bridge via `suspendCancellableCoroutine` |
| `domain/usecase/MatchKnownTargetSignatureUseCase.kt` | Replaces `MatchLearnedSignatureUseCase`; new scoring + penalties |
| `domain/usecase/BuildKnownTargetSignatureUseCase.kt` | Builds `KnownTargetSignature` from `ObservedDevice?` + `GattDiscoveryResult` |
| `domain/usecase/SaveKnownTargetFromCaptureUseCase.kt` | Replaces `SaveLearnedSignatureUseCase` — quick-learn from `CapturedDevice` |
| `domain/usecase/LogLearningEventUseCase.kt` | Appends a `PairedLearningEvent` to Room |
| `ui/viewmodel/PairAndLearnUiState.kt` | State + `PairingFlowState` enum + `PairAndLearnEffect` sealed class |
| `ui/viewmodel/PairAndLearnViewModel.kt` | Orchestrates CDM + GATT + signature build; emits effects |
| `ui/screens/PairAndLearnScreen.kt` | Multi-state Compose UI; handles CDM dialog via `rememberLauncherForActivityResult` |
| `ui/screens/LearningLogScreen.kt` | Shows all past learning sessions from Room |
| `ui/screens/LearningSessionDetailScreen.kt` | Shows events for a single session |

### Modified files
| File | Change |
|------|--------|
| `data/local/WearAwareDatabase.kt` | Bump version 5→6; add two entities + `LearningSessionDao` |
| `di/DatabaseModule.kt` | Provide `LearningSessionDao` |
| `di/RepositoryModule.kt` | Bind `KnownTargetRepository`, `LearningSessionRepository`; remove old `LearnedSignatureRepository` binding |
| `di/BleModule.kt` | Bind `BleGattManager` |
| `ui/navigation/NavGraph.kt` | Add `PairAndLearn`, `LearningLog`, `LearningSessionDetail` routes; "Pair & Learn" button on Scan |
| `ui/screens/ScanScreen.kt` | Add "Pair & Learn" TopAppBar action |
| `ui/viewmodel/CaptureViewModel.kt` | Replace `LearnedSignatureRepository` → `KnownTargetRepository`; all old learned-signature types |
| `ui/viewmodel/CaptureUiState.kt` | Replace `LearnedDeviceSignature` → `KnownTargetSignature`, `LearnedMatchResult` → `KnownTargetMatchResult` |
| `ui/viewmodel/ScanViewModel.kt` | Replace `LearnedSignatureRepository` → `KnownTargetRepository`; migrate match result types |
| `ui/viewmodel/ScanUiState.kt` | Replace `LearnedDeviceSignature` → `KnownTargetSignature`, `LearnedMatchResult` → `KnownTargetMatchResult` |
| `ui/components/DeviceCard.kt` | Replace `LearnedMatchResult`/`LearnedConfidence` → `KnownTargetMatchResult`/`KnownMatchConfidence` |
| `ui/screens/CapturedDeviceDetailScreen.kt` | Replace `LearnedConfidence` → `KnownMatchConfidence`; add WEAK confidence color |

### Deleted files (Task 17)
- `domain/model/LearnedDeviceSignature.kt`
- `domain/model/LearnedMatchResult.kt`
- `domain/model/LearnedMatchInput.kt`
- `domain/repository/LearnedSignatureRepository.kt`
- `data/repository/LearnedSignatureRepositoryImpl.kt`
- `domain/usecase/MatchLearnedSignatureUseCase.kt`
- `domain/usecase/SaveLearnedSignatureUseCase.kt`
- `domain/usecase/ClearLearnedSignatureUseCase.kt`
- `test/.../LearnedSignatureRepositoryImplTest.kt`
- `test/.../MatchLearnedSignatureUseCaseTest.kt`
- `test/.../SaveLearnedSignatureUseCaseTest.kt`
- `test/.../ClearLearnedSignatureUseCaseTest.kt`
- `test/.../CaptureViewModelLearnedTest.kt`

---

## Task 1: Domain models

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/KnownTargetSignature.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/GattDiscoveryResult.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/KnownTargetMatchInput.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/KnownTargetMatchResult.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/PairedLearningSession.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/model/PairedLearningEvent.kt`

- [ ] **Step 1: Write the models**

`KnownTargetSignature.kt`:
```kotlin
package com.wearaware.app.domain.model

/**
 * Persisted BLE + GATT signature for a device the user has confirmed as their glasses.
 * Replaces LearnedDeviceSignature. Stored via SharedPreferences + Gson. One record at a time.
 *
 * DESIGN: No MAC address. gattServiceUuids are empty when learned via quick-learn
 * (capture path); populated when learned via full Pair & Learn flow.
 */
data class KnownTargetSignature(
    val displayName: String,
    val savedAt: Long,
    val fingerprintId: String,
    val manufacturerIds: List<Int>,
    val manufacturerDataPrefixes: List<String>,
    val serviceUuids: List<String>,
    val gattServiceUuids: List<String>,
    val behaviorProfile: KnownBehaviorProfile?
)

data class KnownBehaviorProfile(
    val typicalRssiAtClose: Int,
    val minSeenCount: Int
)
```

`GattDiscoveryResult.kt`:
```kotlin
package com.wearaware.app.domain.model

data class GattDiscoveryResult(
    val deviceAddress: String,
    val serviceUuids: List<String>,
    val characteristicUuids: Map<String, List<String>>,
    val discoveredAt: Long
)
```

`KnownTargetMatchInput.kt`:
```kotlin
package com.wearaware.app.domain.model

data class KnownTargetMatchInput(
    val fingerprintId: String,
    val manufacturerIds: List<Int>,
    val manufacturerDataPrefixes: List<String>,
    val serviceUuids: List<String>,
    val gattServiceUuids: List<String>,
    val averageRssi: Int,
    val seenCount: Int,
    val visibleAtStop: Boolean,
    val connectable: Boolean
)
```

`KnownTargetMatchResult.kt`:
```kotlin
package com.wearaware.app.domain.model

enum class KnownMatchConfidence { STRONG, POSSIBLE, WEAK, NONE }

data class KnownTargetMatchResult(
    val signature: KnownTargetSignature,
    val score: Int,
    val confidence: KnownMatchConfidence,
    val matchedSignals: List<String>,
    /** True for STRONG and POSSIBLE — these get a display label override. */
    val labelOverrideActive: Boolean
)
```

`PairedLearningSession.kt`:
```kotlin
package com.wearaware.app.domain.model

enum class LearningSessionStatus { IN_PROGRESS, COMPLETED, FAILED, ABANDONED }

data class PairedLearningSession(
    val sessionId: String,
    val startedAt: Long,
    val completedAt: Long?,
    val status: LearningSessionStatus,
    val deviceAddress: String?,
    val fingerprintId: String?,
    val eventCount: Int
)
```

`PairedLearningEvent.kt`:
```kotlin
package com.wearaware.app.domain.model

enum class LearningEventType {
    SESSION_STARTED,
    CDM_SCANNING,
    CDM_WAITING_SELECTION,
    CDM_ASSOCIATED,
    CDM_FAILED,
    GATT_CONNECTING,
    GATT_CONNECTED,
    GATT_SERVICES_DISCOVERED,
    GATT_DISCONNECTED,
    GATT_FAILED,
    BLE_SIGNAL_OBSERVED,
    SIGNATURE_BUILT,
    SESSION_COMPLETED
}

data class PairedLearningEvent(
    val eventId: Long,
    val sessionId: String,
    val eventType: LearningEventType,
    val occurredAt: Long,
    val detail: String?
)
```

- [ ] **Step 2: Verify the project compiles with new models only**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: Compile succeeds (new files compile; old files still exist and compile separately).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/model/KnownTargetSignature.kt \
        app/src/main/kotlin/com/wearaware/app/domain/model/GattDiscoveryResult.kt \
        app/src/main/kotlin/com/wearaware/app/domain/model/KnownTargetMatchInput.kt \
        app/src/main/kotlin/com/wearaware/app/domain/model/KnownTargetMatchResult.kt \
        app/src/main/kotlin/com/wearaware/app/domain/model/PairedLearningSession.kt \
        app/src/main/kotlin/com/wearaware/app/domain/model/PairedLearningEvent.kt
git commit -m "feat: domain models — KnownTargetSignature, matching types, learning session/event"
```

---

## Task 2: Room schema v6 — learning session + event tables

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/data/local/LearningSessionEntity.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/data/local/LearningEventEntity.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/data/local/LearningSessionDao.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/data/local/WearAwareDatabase.kt`

- [ ] **Step 1: Write Room entities**

`LearningSessionEntity.kt`:
```kotlin
package com.wearaware.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learning_session")
data class LearningSessionEntity(
    @PrimaryKey val sessionId: String,
    val startedAt: Long,
    val completedAt: Long?,
    val status: String,
    val deviceAddress: String?,
    val fingerprintId: String?,
    val eventCount: Int
)
```

`LearningEventEntity.kt`:
```kotlin
package com.wearaware.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learning_event")
data class LearningEventEntity(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
    val sessionId: String,
    val eventType: String,
    val occurredAt: Long,
    val detail: String?
)
```

- [ ] **Step 2: Write the DAO**

`LearningSessionDao.kt`:
```kotlin
package com.wearaware.app.data.local

import androidx.room.*

@Dao
interface LearningSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: LearningSessionEntity)

    @Query("UPDATE learning_session SET status = :status, completedAt = :completedAt WHERE sessionId = :sessionId")
    suspend fun updateStatus(sessionId: String, status: String, completedAt: Long?)

    @Query("UPDATE learning_session SET deviceAddress = :address WHERE sessionId = :sessionId")
    suspend fun updateDeviceAddress(sessionId: String, address: String)

    @Query("UPDATE learning_session SET fingerprintId = :fingerprintId WHERE sessionId = :sessionId")
    suspend fun updateFingerprintId(sessionId: String, fingerprintId: String)

    @Query("UPDATE learning_session SET eventCount = eventCount + 1 WHERE sessionId = :sessionId")
    suspend fun incrementEventCount(sessionId: String)

    @Query("SELECT * FROM learning_session ORDER BY startedAt DESC")
    suspend fun getAllSessions(): List<LearningSessionEntity>

    @Query("SELECT * FROM learning_session WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): LearningSessionEntity?

    @Insert
    suspend fun insertEvent(event: LearningEventEntity): Long

    @Query("SELECT * FROM learning_event WHERE sessionId = :sessionId ORDER BY occurredAt ASC")
    suspend fun getEventsForSession(sessionId: String): List<LearningEventEntity>
}
```

- [ ] **Step 3: Bump WearAwareDatabase to version 6**

In `app/src/main/kotlin/com/wearaware/app/data/local/WearAwareDatabase.kt`, replace the entire file:
```kotlin
package com.wearaware.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * VERSION HISTORY:
 *   1 → initial schema
 *   2 → added targetMatchScore, targetMatchReason, isTopCandidate
 *   3 → added fingerprintId, manufacturerIds
 *   4 → added manufacturerDataHex, serviceUuids, txPower, connectable, rawScanBytesHex
 *   5 → added capture_session and captured_device tables
 *   6 → added learning_session and learning_event tables
 * NOTES: fallbackToDestructiveMigration used — all tables are ephemeral/user-clearable.
 */
@Database(
    entities = [
        ScanLogEntity::class,
        CaptureSessionEntity::class,
        CapturedDeviceEntity::class,
        LearningSessionEntity::class,
        LearningEventEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class WearAwareDatabase : RoomDatabase() {
    abstract fun scanLogDao(): ScanLogDao
    abstract fun captureDao(): CaptureDao
    abstract fun learningSessionDao(): LearningSessionDao

    companion object {
        const val DATABASE_NAME = "wearaware_db"
    }
}
```

- [ ] **Step 4: Verify compile**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: Compile succeeds (no Room entity errors).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/data/local/LearningSessionEntity.kt \
        app/src/main/kotlin/com/wearaware/app/data/local/LearningEventEntity.kt \
        app/src/main/kotlin/com/wearaware/app/data/local/LearningSessionDao.kt \
        app/src/main/kotlin/com/wearaware/app/data/local/WearAwareDatabase.kt
git commit -m "feat: Room schema v6 — learning_session and learning_event tables"
```

---

## Task 3: KnownTargetRepository — interface + SharedPreferences impl

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/domain/repository/KnownTargetRepository.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/data/repository/KnownTargetRepositoryImpl.kt`
- Create: `app/src/test/kotlin/com/wearaware/app/data/repository/KnownTargetRepositoryImplTest.kt`

- [ ] **Step 1: Write the failing test**

`KnownTargetRepositoryImplTest.kt`:
```kotlin
package com.wearaware.app.data.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import com.wearaware.app.domain.model.*
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class KnownTargetRepositoryImplTest {

    private val prefs = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val gson = Gson()
    private lateinit var repo: KnownTargetRepositoryImpl

    private val testSignature = KnownTargetSignature(
        displayName = "My Meta Glasses",
        savedAt = 1_000_000L,
        fingerprintId = "fp-abc123",
        manufacturerIds = listOf(0x0075),
        manufacturerDataPrefixes = listOf("0075:deadbeef"),
        serviceUuids = listOf("0000fe2c-0000-1000-8000-00805f9b34fb"),
        gattServiceUuids = listOf("0000180a-0000-1000-8000-00805f9b34fb"),
        behaviorProfile = KnownBehaviorProfile(typicalRssiAtClose = -55, minSeenCount = 80)
    )

    @Before
    fun setUp() {
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        repo = KnownTargetRepositoryImpl(prefs, gson)
    }

    @Test
    fun `load returns null when key is missing`() {
        every { prefs.getString(KEY_KNOWN_TARGET_SIGNATURE, null) } returns null
        assertNull(repo.load())
    }

    @Test
    fun `load returns null when JSON is malformed`() {
        every { prefs.getString(KEY_KNOWN_TARGET_SIGNATURE, null) } returns "not-json"
        assertNull(repo.load())
    }

    @Test
    fun `save then load roundtrip returns identical signature`() {
        val stored = slot<String>()
        every { editor.putString(KEY_KNOWN_TARGET_SIGNATURE, capture(stored)) } returns editor
        repo.save(testSignature)
        every { prefs.getString(KEY_KNOWN_TARGET_SIGNATURE, null) } returns stored.captured
        assertEquals(testSignature, repo.load())
    }

    @Test
    fun `save commits synchronously`() {
        repo.save(testSignature)
        verify { editor.putString(KEY_KNOWN_TARGET_SIGNATURE, any()) }
        verify { editor.commit() }
    }

    @Test
    fun `clear removes the stored key`() {
        repo.clear()
        verify { editor.remove(KEY_KNOWN_TARGET_SIGNATURE) }
        verify { editor.apply() }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:test --tests "com.wearaware.app.data.repository.KnownTargetRepositoryImplTest" 2>&1 | tail -20
```
Expected: FAIL — `KnownTargetRepositoryImpl` not found.

- [ ] **Step 3: Write the interface and implementation**

`KnownTargetRepository.kt`:
```kotlin
package com.wearaware.app.domain.repository

import com.wearaware.app.domain.model.KnownTargetSignature

interface KnownTargetRepository {
    fun load(): KnownTargetSignature?
    fun save(signature: KnownTargetSignature)
    fun clear()
}
```

`KnownTargetRepositoryImpl.kt`:
```kotlin
package com.wearaware.app.data.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import com.wearaware.app.domain.model.KnownTargetSignature
import com.wearaware.app.domain.repository.KnownTargetRepository
import javax.inject.Inject
import javax.inject.Singleton

internal const val KEY_KNOWN_TARGET_SIGNATURE = "known_target_signature"

@Singleton
class KnownTargetRepositoryImpl @Inject constructor(
    private val prefs: SharedPreferences,
    private val gson: Gson
) : KnownTargetRepository {

    override fun load(): KnownTargetSignature? {
        val json = prefs.getString(KEY_KNOWN_TARGET_SIGNATURE, null) ?: return null
        return runCatching { gson.fromJson(json, KnownTargetSignature::class.java) }.getOrNull()
    }

    override fun save(signature: KnownTargetSignature) {
        prefs.edit().putString(KEY_KNOWN_TARGET_SIGNATURE, gson.toJson(signature)).commit()
    }

    override fun clear() {
        prefs.edit().remove(KEY_KNOWN_TARGET_SIGNATURE).apply()
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:test --tests "com.wearaware.app.data.repository.KnownTargetRepositoryImplTest" 2>&1 | tail -20
```
Expected: 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/repository/KnownTargetRepository.kt \
        app/src/main/kotlin/com/wearaware/app/data/repository/KnownTargetRepositoryImpl.kt \
        app/src/test/kotlin/com/wearaware/app/data/repository/KnownTargetRepositoryImplTest.kt
git commit -m "feat: KnownTargetRepository — interface + SharedPreferences impl"
```

---

## Task 4: LearningSessionRepository — interface + Room impl

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/domain/repository/LearningSessionRepository.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/data/repository/LearningSessionRepositoryImpl.kt`

- [ ] **Step 1: Write the interface**

`LearningSessionRepository.kt`:
```kotlin
package com.wearaware.app.domain.repository

import com.wearaware.app.domain.model.LearningEventType
import com.wearaware.app.domain.model.LearningSessionStatus
import com.wearaware.app.domain.model.PairedLearningEvent
import com.wearaware.app.domain.model.PairedLearningSession

interface LearningSessionRepository {
    suspend fun createSession(sessionId: String, startedAt: Long)
    suspend fun updateStatus(sessionId: String, status: LearningSessionStatus)
    suspend fun updateDeviceAddress(sessionId: String, address: String)
    suspend fun updateFingerprintId(sessionId: String, fingerprintId: String)
    suspend fun getAllSessions(): List<PairedLearningSession>
    suspend fun getSession(sessionId: String): PairedLearningSession?
    suspend fun appendEvent(
        sessionId: String,
        eventType: LearningEventType,
        occurredAt: Long,
        detail: String?
    ): PairedLearningEvent
    suspend fun getEventsForSession(sessionId: String): List<PairedLearningEvent>
}
```

- [ ] **Step 2: Write the Room impl**

`LearningSessionRepositoryImpl.kt`:
```kotlin
package com.wearaware.app.data.repository

import com.wearaware.app.data.local.LearningEventEntity
import com.wearaware.app.data.local.LearningSessionDao
import com.wearaware.app.data.local.LearningSessionEntity
import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.LearningSessionRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LearningSessionRepositoryImpl @Inject constructor(
    private val dao: LearningSessionDao
) : LearningSessionRepository {

    override suspend fun createSession(sessionId: String, startedAt: Long) {
        dao.insertSession(
            LearningSessionEntity(
                sessionId = sessionId,
                startedAt = startedAt,
                completedAt = null,
                status = LearningSessionStatus.IN_PROGRESS.name,
                deviceAddress = null,
                fingerprintId = null,
                eventCount = 0
            )
        )
    }

    override suspend fun updateStatus(sessionId: String, status: LearningSessionStatus) {
        val completedAt = if (status != LearningSessionStatus.IN_PROGRESS) System.currentTimeMillis() else null
        dao.updateStatus(sessionId, status.name, completedAt)
    }

    override suspend fun updateDeviceAddress(sessionId: String, address: String) {
        dao.updateDeviceAddress(sessionId, address)
    }

    override suspend fun updateFingerprintId(sessionId: String, fingerprintId: String) {
        dao.updateFingerprintId(sessionId, fingerprintId)
    }

    override suspend fun getAllSessions(): List<PairedLearningSession> =
        dao.getAllSessions().map { it.toDomain() }

    override suspend fun getSession(sessionId: String): PairedLearningSession? =
        dao.getSession(sessionId)?.toDomain()

    override suspend fun appendEvent(
        sessionId: String,
        eventType: LearningEventType,
        occurredAt: Long,
        detail: String?
    ): PairedLearningEvent {
        val id = dao.insertEvent(
            LearningEventEntity(
                sessionId = sessionId,
                eventType = eventType.name,
                occurredAt = occurredAt,
                detail = detail
            )
        )
        dao.incrementEventCount(sessionId)
        return PairedLearningEvent(
            eventId = id,
            sessionId = sessionId,
            eventType = eventType,
            occurredAt = occurredAt,
            detail = detail
        )
    }

    override suspend fun getEventsForSession(sessionId: String): List<PairedLearningEvent> =
        dao.getEventsForSession(sessionId).map { it.toDomain() }

    private fun LearningSessionEntity.toDomain() = PairedLearningSession(
        sessionId = sessionId,
        startedAt = startedAt,
        completedAt = completedAt,
        status = LearningSessionStatus.valueOf(status),
        deviceAddress = deviceAddress,
        fingerprintId = fingerprintId,
        eventCount = eventCount
    )

    private fun LearningEventEntity.toDomain() = PairedLearningEvent(
        eventId = eventId,
        sessionId = sessionId,
        eventType = LearningEventType.valueOf(eventType),
        occurredAt = occurredAt,
        detail = detail
    )
}
```

- [ ] **Step 3: Verify compile**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: Compiles cleanly.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/repository/LearningSessionRepository.kt \
        app/src/main/kotlin/com/wearaware/app/data/repository/LearningSessionRepositoryImpl.kt
git commit -m "feat: LearningSessionRepository — interface + Room impl"
```

---

## Task 5: BleGattManager

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/data/ble/BleGattManager.kt`

- [ ] **Step 1: Write `BleGattManager`**

`BleGattManager.kt`:
```kotlin
package com.wearaware.app.data.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.wearaware.app.domain.model.GattDiscoveryResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface BleGattManager {
    suspend fun connectAndDiscover(deviceAddress: String): GattDiscoveryResult
}

@Singleton
class BleGattManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : BleGattManager {

    override suspend fun connectAndDiscover(deviceAddress: String): GattDiscoveryResult {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val device = adapter.getRemoteDevice(deviceAddress)
        var gatt: BluetoothGatt? = null

        return try {
            suspendCancellableCoroutine { cont ->
                val callback = object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                        when (newState) {
                            BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                            BluetoothProfile.STATE_DISCONNECTED -> {
                                if (cont.isActive) cont.resumeWithException(
                                    IOException("GATT disconnected before service discovery (status=$status)")
                                )
                            }
                        }
                    }

                    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                        g.disconnect()
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            val serviceUuids = g.services.map { it.uuid.toString() }
                            val charUuids = g.services.associate { s ->
                                s.uuid.toString() to s.characteristics.map { it.uuid.toString() }
                            }
                            cont.resume(
                                GattDiscoveryResult(
                                    deviceAddress = deviceAddress,
                                    serviceUuids = serviceUuids,
                                    characteristicUuids = charUuids,
                                    discoveredAt = System.currentTimeMillis()
                                )
                            )
                        } else {
                            cont.resumeWithException(IOException("Service discovery failed (status=$status)"))
                        }
                    }
                }
                gatt = device.connectGatt(context, false, callback)
                cont.invokeOnCancellation {
                    gatt?.disconnect()
                    gatt?.close()
                }
            }
        } finally {
            gatt?.close()
        }
    }
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: Compiles cleanly.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/data/ble/BleGattManager.kt
git commit -m "feat: BleGattManager — GATT connect + service discovery via suspendCancellableCoroutine"
```

---

## Task 6: DI wiring

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/di/DatabaseModule.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/di/RepositoryModule.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/di/BleModule.kt`

- [ ] **Step 1: Add `LearningSessionDao` provider to DatabaseModule**

In `DatabaseModule.kt`, add after the `provideCaptureDao` method:
```kotlin
    @Provides
    fun provideLearningSessionDao(database: WearAwareDatabase): LearningSessionDao =
        database.learningSessionDao()
```

Also add the import:
```kotlin
import com.wearaware.app.data.local.LearningSessionDao
```

- [ ] **Step 2: Update RepositoryModule**

Replace the entire `RepositoryModule.kt`:
```kotlin
package com.wearaware.app.di

import com.wearaware.app.data.repository.BleRepositoryImpl
import com.wearaware.app.data.repository.CaptureRepositoryImpl
import com.wearaware.app.data.repository.KnownTargetRepositoryImpl
import com.wearaware.app.data.repository.LearningSessionRepositoryImpl
import com.wearaware.app.data.repository.ScanLogRepositoryImpl
import com.wearaware.app.domain.repository.BleRepository
import com.wearaware.app.domain.repository.CaptureRepository
import com.wearaware.app.domain.repository.KnownTargetRepository
import com.wearaware.app.domain.repository.LearningSessionRepository
import com.wearaware.app.domain.repository.ScanLogRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindBleRepository(impl: BleRepositoryImpl): BleRepository

    @Binds @Singleton
    abstract fun bindScanLogRepository(impl: ScanLogRepositoryImpl): ScanLogRepository

    @Binds @Singleton
    abstract fun bindCaptureRepository(impl: CaptureRepositoryImpl): CaptureRepository

    @Binds @Singleton
    abstract fun bindKnownTargetRepository(impl: KnownTargetRepositoryImpl): KnownTargetRepository

    @Binds @Singleton
    abstract fun bindLearningSessionRepository(impl: LearningSessionRepositoryImpl): LearningSessionRepository
}
```

- [ ] **Step 3: Bind BleGattManager in BleModule**

In `BleModule.kt`, add the binding (this is a `@Binds` method in an object, so we need to convert to abstract class or use `@Provides`). Since `BleModule` is currently an `object`, add a `@Provides` method:
```kotlin
    @Provides
    @Singleton
    fun provideBleGattManager(impl: BleGattManagerImpl): BleGattManager = impl
```

Also add these imports to `BleModule.kt`:
```kotlin
import com.wearaware.app.data.ble.BleGattManager
import com.wearaware.app.data.ble.BleGattManagerImpl
```

- [ ] **Step 4: Verify compile**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: Compiles cleanly. (Old `LearnedSignatureRepository` binding is gone; old code that depends on it will fail at compile time — that's fine, we'll fix it in Tasks 14–16.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/di/DatabaseModule.kt \
        app/src/main/kotlin/com/wearaware/app/di/RepositoryModule.kt \
        app/src/main/kotlin/com/wearaware/app/di/BleModule.kt
git commit -m "feat: DI wiring — KnownTargetRepository, LearningSessionRepository, BleGattManager"
```

---

## Task 7: MatchKnownTargetSignatureUseCase (TDD)

**Files:**
- Create: `app/src/test/kotlin/com/wearaware/app/domain/usecase/MatchKnownTargetSignatureUseCaseTest.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/usecase/MatchKnownTargetSignatureUseCase.kt`

**Scoring rules:**
- +6 if any manufacturerId overlaps (flat bonus)
- +5 per manufacturer data prefix match
- +4 per service UUID overlap
- +3 per GATT service UUID overlap
- +3 if fingerprintId matches exactly
- +3 if averageRssi >= -60
- +3 if seenCount >= 50
- +2 if visibleAtStop
- +2 if connectable
- **Penalties:** -5 if ALL manufacturerIds are Apple (0x004C only), -4 if averageRssi < -80, -3 if seenCount < 5
- **Thresholds:** STRONG ≥ 12, POSSIBLE ≥ 6, WEAK ≥ 3, NONE < 3
- `labelOverrideActive` = true when confidence is STRONG or POSSIBLE

- [ ] **Step 1: Write the failing tests**

`MatchKnownTargetSignatureUseCaseTest.kt`:
```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class MatchKnownTargetSignatureUseCaseTest {

    private val useCase = MatchKnownTargetSignatureUseCase()

    private fun makeSignature(
        fingerprintId: String = "saved-fp",
        manufacturerIds: List<Int> = listOf(0x0075),
        prefixes: List<String> = listOf("0075:deadbeef"),
        serviceUuids: List<String> = listOf("uuid-glasses"),
        gattServiceUuids: List<String> = listOf("gatt-service-1")
    ) = KnownTargetSignature(
        displayName = "My Meta Glasses",
        savedAt = 1000L,
        fingerprintId = fingerprintId,
        manufacturerIds = manufacturerIds,
        manufacturerDataPrefixes = prefixes,
        serviceUuids = serviceUuids,
        gattServiceUuids = gattServiceUuids,
        behaviorProfile = null
    )

    private fun makeInput(
        fingerprintId: String = "candidate-fp",
        manufacturerIds: List<Int> = emptyList(),
        prefixes: List<String> = emptyList(),
        serviceUuids: List<String> = emptyList(),
        gattServiceUuids: List<String> = emptyList(),
        averageRssi: Int = -75,
        seenCount: Int = 10,
        visibleAtStop: Boolean = false,
        connectable: Boolean = false
    ) = KnownTargetMatchInput(
        fingerprintId = fingerprintId,
        manufacturerIds = manufacturerIds,
        manufacturerDataPrefixes = prefixes,
        serviceUuids = serviceUuids,
        gattServiceUuids = gattServiceUuids,
        averageRssi = averageRssi,
        seenCount = seenCount,
        visibleAtStop = visibleAtStop,
        connectable = connectable
    )

    @Test
    fun `mfr data prefix + mfr ID + GATT service produces STRONG`() {
        // +5 prefix + +6 mfrId + +3 gatt = 14 → STRONG
        val result = useCase(
            makeInput(
                manufacturerIds = listOf(0x0075),
                prefixes = listOf("0075:deadbeef"),
                gattServiceUuids = listOf("gatt-service-1")
            ),
            makeSignature()
        )
        assertEquals(KnownMatchConfidence.STRONG, result.confidence)
        assertTrue(result.labelOverrideActive)
        assertTrue(result.score >= 12)
    }

    @Test
    fun `mfr ID only with high persistence produces POSSIBLE`() {
        // +6 mfrId + +3 seenCount = 9 → POSSIBLE (not STRONG: <12)
        val result = useCase(
            makeInput(manufacturerIds = listOf(0x0075), seenCount = 60),
            makeSignature()
        )
        assertEquals(KnownMatchConfidence.POSSIBLE, result.confidence)
        assertTrue(result.labelOverrideActive)
    }

    @Test
    fun `mfr ID only at low persistence produces WEAK`() {
        // +6 mfrId only, seenCount 10 = no persistence bonus → score 6 → POSSIBLE
        // Actually +6 alone = 6 → POSSIBLE threshold
        // For WEAK: no mfrId, one service UUID only → +4 = 4 → POSSIBLE
        // For WEAK: just visibleAtStop + connectable → +2 + +2 = 4 → POSSIBLE
        // For WEAK: one service UUID with no other signals → +4 = 4 → POSSIBLE
        // WEAK ≥ 3 and < 6:
        // e.g. fingerprint match only → +3 = WEAK
        val result = useCase(
            makeInput(fingerprintId = "saved-fp"),
            makeSignature(fingerprintId = "saved-fp", manufacturerIds = listOf(0x9999), prefixes = emptyList(), serviceUuids = emptyList(), gattServiceUuids = emptyList())
        )
        assertEquals(KnownMatchConfidence.WEAK, result.confidence)
        assertFalse(result.labelOverrideActive)
    }

    @Test
    fun `no matching signals produces NONE`() {
        val result = useCase(
            makeInput(),  // empty, -80 rssi, seenCount 10
            makeSignature()
        )
        // -4 (rssi < -80? rssi is exactly -75, no penalty) → 0 score → NONE
        assertEquals(KnownMatchConfidence.NONE, result.confidence)
        assertFalse(result.labelOverrideActive)
        assertEquals(0, result.score)
    }

    @Test
    fun `Apple-only manufacturer ID applies penalty`() {
        // +6 apple mfr id match - 5 apple penalty = 1 → NONE
        val result = useCase(
            makeInput(manufacturerIds = listOf(0x004C)),
            makeSignature(manufacturerIds = listOf(0x004C), prefixes = emptyList(), serviceUuids = emptyList(), gattServiceUuids = emptyList())
        )
        assertTrue("Apple penalty should reduce score", result.score < 6)
    }

    @Test
    fun `very weak signal applies rssi penalty`() {
        // rssi = -85 → -4 penalty applied
        val result = useCase(
            makeInput(averageRssi = -85),
            makeSignature()
        )
        val resultNoPenalty = useCase(
            makeInput(averageRssi = -75),
            makeSignature()
        )
        assertTrue(result.score < resultNoPenalty.score)
    }

    @Test
    fun `very low seenCount applies persistence penalty`() {
        // seenCount = 3 → -3 penalty
        val result = useCase(
            makeInput(seenCount = 3),
            makeSignature()
        )
        val resultNoPenalty = useCase(
            makeInput(seenCount = 10),
            makeSignature()
        )
        assertTrue(result.score < resultNoPenalty.score)
    }

    @Test
    fun `visibleAtStop and connectable each add bonus`() {
        val base = useCase(makeInput(), makeSignature())
        val withBonuses = useCase(
            makeInput(visibleAtStop = true, connectable = true),
            makeSignature()
        )
        assertEquals(4, withBonuses.score - base.score)
    }

    @Test
    fun `matched signals list is populated for each scoring contribution`() {
        val result = useCase(
            makeInput(
                manufacturerIds = listOf(0x0075),
                prefixes = listOf("0075:deadbeef"),
                gattServiceUuids = listOf("gatt-service-1"),
                seenCount = 60
            ),
            makeSignature()
        )
        assertTrue(result.matchedSignals.any { it.contains("Manufacturer") })
        assertTrue(result.matchedSignals.any { it.contains("prefix") || it.contains("Prefix") })
        assertTrue(result.matchedSignals.any { it.contains("GATT") || it.contains("gatt") })
        assertTrue(result.matchedSignals.any { it.contains("persistence") || it.contains("Persistence") || it.contains("50") })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:test --tests "com.wearaware.app.domain.usecase.MatchKnownTargetSignatureUseCaseTest" 2>&1 | tail -20
```
Expected: FAIL — class not found.

- [ ] **Step 3: Write the implementation**

`MatchKnownTargetSignatureUseCase.kt`:
```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import javax.inject.Inject

/**
 * Scores a candidate device against a saved KnownTargetSignature.
 *
 * BONUSES:
 *   +6 any manufacturerId overlap (flat)
 *   +5 per manufacturer data prefix match
 *   +4 per service UUID overlap
 *   +3 per GATT service UUID overlap
 *   +3 if fingerprintId exactly matches
 *   +3 if averageRssi >= -60 dBm
 *   +3 if seenCount >= 50
 *   +2 if visibleAtStop = true
 *   +2 if connectable = true
 *
 * PENALTIES:
 *   -5 if ALL manufacturerIds == Apple (0x004C) — Apple noise filter
 *   -4 if averageRssi < -80 dBm
 *   -3 if seenCount < 5
 *
 * THRESHOLDS: STRONG >= 12 | POSSIBLE >= 6 | WEAK >= 3 | NONE < 3
 * labelOverrideActive = true for STRONG and POSSIBLE
 */
class MatchKnownTargetSignatureUseCase @Inject constructor() {

    operator fun invoke(
        input: KnownTargetMatchInput,
        signature: KnownTargetSignature
    ): KnownTargetMatchResult {
        var score = 0
        val signals = mutableListOf<String>()

        // +6 flat bonus for any manufacturerId overlap
        val sharedIds = input.manufacturerIds.intersect(signature.manufacturerIds.toSet())
        if (sharedIds.isNotEmpty()) {
            score += 6
            val hex = sharedIds.joinToString { "0x${it.toString(16).uppercase().padStart(4, '0')}" }
            signals += "Manufacturer ID overlap: $hex (+6)"
        }

        // +5 per manufacturer data prefix match
        val matchedPrefixes = input.manufacturerDataPrefixes.intersect(signature.manufacturerDataPrefixes.toSet())
        for (prefix in matchedPrefixes) {
            score += 5
            signals += "Manufacturer data prefix match: $prefix (+5)"
        }

        // +4 per service UUID overlap
        val sharedUuids = input.serviceUuids.intersect(signature.serviceUuids.toSet())
        for (uuid in sharedUuids) {
            score += 4
            signals += "Service UUID match: $uuid (+4)"
        }

        // +3 per GATT service UUID overlap
        val sharedGattUuids = input.gattServiceUuids.intersect(signature.gattServiceUuids.toSet())
        for (uuid in sharedGattUuids) {
            score += 3
            signals += "GATT service match: $uuid (+3)"
        }

        // +3 if fingerprintId matches exactly
        if (input.fingerprintId == signature.fingerprintId) {
            score += 3
            signals += "FingerprintId exact match (+3)"
        }

        // +3 if close proximity
        if (input.averageRssi >= -60) {
            score += 3
            signals += "Close proximity: ${input.averageRssi} dBm (+3)"
        }

        // +3 if high persistence
        if (input.seenCount >= 50) {
            score += 3
            signals += "High persistence: ${input.seenCount} observations (+3)"
        }

        // +2 if visible at stop
        if (input.visibleAtStop) {
            score += 2
            signals += "Still visible at capture stop (+2)"
        }

        // +2 if connectable
        if (input.connectable) {
            score += 2
            signals += "Device is connectable (+2)"
        }

        // --- Penalties ---

        // -5 Apple-only noise filter
        if (input.manufacturerIds.isNotEmpty() && input.manufacturerIds.all { it == 0x004C }) {
            score -= 5
            signals += "Apple-only manufacturer ID — noise filter (-5)"
        }

        // -4 very weak signal
        if (input.averageRssi < -80) {
            score -= 4
            signals += "Very weak signal: ${input.averageRssi} dBm (-4)"
        }

        // -3 very low persistence
        if (input.seenCount < 5) {
            score -= 3
            signals += "Very low persistence: ${input.seenCount} observations (-3)"
        }

        val confidence = when {
            score >= 12 -> KnownMatchConfidence.STRONG
            score >= 6  -> KnownMatchConfidence.POSSIBLE
            score >= 3  -> KnownMatchConfidence.WEAK
            else        -> KnownMatchConfidence.NONE
        }

        return KnownTargetMatchResult(
            signature = signature,
            score = score,
            confidence = confidence,
            matchedSignals = signals,
            labelOverrideActive = confidence == KnownMatchConfidence.STRONG || confidence == KnownMatchConfidence.POSSIBLE
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:test --tests "com.wearaware.app.domain.usecase.MatchKnownTargetSignatureUseCaseTest" 2>&1 | tail -20
```
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/usecase/MatchKnownTargetSignatureUseCase.kt \
        app/src/test/kotlin/com/wearaware/app/domain/usecase/MatchKnownTargetSignatureUseCaseTest.kt
git commit -m "feat: MatchKnownTargetSignatureUseCase — richer scoring with GATT signals and penalties"
```

---

## Task 8: BuildKnownTargetSignatureUseCase (TDD)

**Files:**
- Create: `app/src/test/kotlin/com/wearaware/app/domain/usecase/BuildKnownTargetSignatureUseCaseTest.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/usecase/BuildKnownTargetSignatureUseCase.kt`

- [ ] **Step 1: Write the failing tests**

`BuildKnownTargetSignatureUseCaseTest.kt`:
```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class BuildKnownTargetSignatureUseCaseTest {

    private val useCase = BuildKnownTargetSignatureUseCase()

    private fun makeFingerprint(
        fingerprintId: String = "fp-abc",
        manufacturerIds: List<Int> = listOf(0x0075),
        manufacturerDataHex: Map<Int, String> = mapOf(0x0075 to "deadbeef01234567"),
        serviceUuids: List<String> = listOf("uuid-glasses")
    ) = DeviceFingerprint(
        fingerprintId = fingerprintId,
        manufacturerIds = manufacturerIds,
        manufacturerNames = listOf("Meta"),
        manufacturerDataHex = manufacturerDataHex,
        serviceUuids = serviceUuids,
        normalizedName = null,
        txPower = null
    )

    private fun makeObservedDevice(
        id: String = "fp-abc",
        fingerprint: DeviceFingerprint? = makeFingerprint(),
        averagedRssi: Int = -57,
        seenCount: Int = 100
    ) = ObservedDevice(
        id = id,
        advertisedName = "Meta Smart Glasses",
        rawRssi = -57,
        averagedRssi = averagedRssi,
        proximityLabel = ProximityLabel.CLOSE,
        visibilityState = VisibilityState.DETECTED_NOW,
        firstSeenAt = 0L,
        lastSeenAt = 0L,
        seenCount = seenCount,
        classification = ClassificationResult(DeviceCategory.UNKNOWN_BLE_DEVICE, "", MatchConfidence.LOW),
        persistenceAlert = null,
        fingerprint = fingerprint
    )

    private fun makeGattResult(
        deviceAddress: String = "AA:BB:CC:DD:EE:FF",
        serviceUuids: List<String> = listOf("0000180a-0000-1000-8000-00805f9b34fb")
    ) = GattDiscoveryResult(
        deviceAddress = deviceAddress,
        serviceUuids = serviceUuids,
        characteristicUuids = mapOf("0000180a-0000-1000-8000-00805f9b34fb" to listOf("characteristic-1")),
        discoveredAt = 1000L
    )

    @Test
    fun `builds signature with manufacturer data prefixes from observed device`() {
        val sig = useCase(makeObservedDevice(), makeGattResult())
        assertEquals(listOf("0075:deadbeef"), sig.manufacturerDataPrefixes)
    }

    @Test
    fun `builds signature with GATT service UUIDs`() {
        val sig = useCase(makeObservedDevice(), makeGattResult())
        assertEquals(listOf("0000180a-0000-1000-8000-00805f9b34fb"), sig.gattServiceUuids)
    }

    @Test
    fun `builds signature with manufacturer IDs from fingerprint`() {
        val sig = useCase(makeObservedDevice(), makeGattResult())
        assertEquals(listOf(0x0075), sig.manufacturerIds)
    }

    @Test
    fun `builds signature with fingerprintId from observed device`() {
        val sig = useCase(makeObservedDevice(id = "my-fp"), makeGattResult())
        assertEquals("my-fp", sig.fingerprintId)
    }

    @Test
    fun `builds behavior profile from observed device`() {
        val sig = useCase(makeObservedDevice(averagedRssi = -55, seenCount = 80), makeGattResult())
        assertNotNull(sig.behaviorProfile)
        assertEquals(-55, sig.behaviorProfile!!.typicalRssiAtClose)
        assertEquals(80, sig.behaviorProfile.minSeenCount)
    }

    @Test
    fun `falls back to gatt device address as fingerprintId when observed device is null`() {
        val sig = useCase(null, makeGattResult(deviceAddress = "AA:BB:CC:DD:EE:FF"))
        assertEquals("AA:BB:CC:DD:EE:FF", sig.fingerprintId)
    }

    @Test
    fun `when observed device is null manufacturer fields are empty`() {
        val sig = useCase(null, makeGattResult())
        assertTrue(sig.manufacturerIds.isEmpty())
        assertTrue(sig.manufacturerDataPrefixes.isEmpty())
        assertTrue(sig.serviceUuids.isEmpty())
        assertNull(sig.behaviorProfile)
    }

    @Test
    fun `displayName is always My Meta Glasses`() {
        val sig = useCase(makeObservedDevice(), makeGattResult())
        assertEquals("My Meta Glasses", sig.displayName)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:test --tests "com.wearaware.app.domain.usecase.BuildKnownTargetSignatureUseCaseTest" 2>&1 | tail -20
```
Expected: FAIL — class not found.

- [ ] **Step 3: Write the implementation**

`BuildKnownTargetSignatureUseCase.kt`:
```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import javax.inject.Inject

/**
 * Builds a KnownTargetSignature from BLE advertising data (ObservedDevice) +
 * GATT service discovery result. The full Pair & Learn path uses both inputs.
 * If observedDevice is null (device not in active scan), only GATT data is used.
 */
class BuildKnownTargetSignatureUseCase @Inject constructor() {

    operator fun invoke(
        observedDevice: ObservedDevice?,
        gattResult: GattDiscoveryResult
    ): KnownTargetSignature {
        val prefixes = extractPrefixesFromFingerprintMap(observedDevice?.fingerprint?.manufacturerDataHex)
        return KnownTargetSignature(
            displayName = "My Meta Glasses",
            savedAt = System.currentTimeMillis(),
            fingerprintId = observedDevice?.id ?: gattResult.deviceAddress,
            manufacturerIds = observedDevice?.fingerprint?.manufacturerIds ?: emptyList(),
            manufacturerDataPrefixes = prefixes,
            serviceUuids = observedDevice?.fingerprint?.serviceUuids ?: emptyList(),
            gattServiceUuids = gattResult.serviceUuids,
            behaviorProfile = observedDevice?.let {
                KnownBehaviorProfile(
                    typicalRssiAtClose = it.averagedRssi,
                    minSeenCount = it.seenCount
                )
            }
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :app:test --tests "com.wearaware.app.domain.usecase.BuildKnownTargetSignatureUseCaseTest" 2>&1 | tail -20
```
Expected: All tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/usecase/BuildKnownTargetSignatureUseCase.kt \
        app/src/test/kotlin/com/wearaware/app/domain/usecase/BuildKnownTargetSignatureUseCaseTest.kt
git commit -m "feat: BuildKnownTargetSignatureUseCase — combine BLE advertising + GATT discovery"
```

---

## Task 9: SaveKnownTargetFromCaptureUseCase + LogLearningEventUseCase

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/domain/usecase/SaveKnownTargetFromCaptureUseCase.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/domain/usecase/LogLearningEventUseCase.kt`

- [ ] **Step 1: Write the use cases**

`SaveKnownTargetFromCaptureUseCase.kt`:
```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.KnownTargetRepository
import javax.inject.Inject

/**
 * Quick-learn path: builds a KnownTargetSignature from a CapturedDevice (no GATT data).
 * Replaces SaveLearnedSignatureUseCase. gattServiceUuids is empty on this path.
 */
class SaveKnownTargetFromCaptureUseCase @Inject constructor(
    private val repository: KnownTargetRepository
) {
    operator fun invoke(device: CapturedDevice): KnownTargetSignature {
        val prefixes = extractPrefixesFromSummary(device.manufacturerDataSummary)
        val signature = KnownTargetSignature(
            displayName = "My Meta Glasses",
            savedAt = System.currentTimeMillis(),
            fingerprintId = device.fingerprintId,
            manufacturerIds = device.manufacturerIds,
            manufacturerDataPrefixes = prefixes,
            serviceUuids = device.serviceUuids,
            gattServiceUuids = emptyList(),
            behaviorProfile = KnownBehaviorProfile(
                typicalRssiAtClose = device.averageRssi,
                minSeenCount = device.seenCount
            )
        )
        repository.save(signature)
        return signature
    }
}
```

`LogLearningEventUseCase.kt`:
```kotlin
package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.LearningEventType
import com.wearaware.app.domain.model.PairedLearningEvent
import com.wearaware.app.domain.repository.LearningSessionRepository
import javax.inject.Inject

class LogLearningEventUseCase @Inject constructor(
    private val repository: LearningSessionRepository
) {
    suspend operator fun invoke(
        sessionId: String,
        eventType: LearningEventType,
        detail: String? = null
    ): PairedLearningEvent = repository.appendEvent(
        sessionId = sessionId,
        eventType = eventType,
        occurredAt = System.currentTimeMillis(),
        detail = detail
    )
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: Compiles cleanly.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/domain/usecase/SaveKnownTargetFromCaptureUseCase.kt \
        app/src/main/kotlin/com/wearaware/app/domain/usecase/LogLearningEventUseCase.kt
git commit -m "feat: SaveKnownTargetFromCaptureUseCase (quick-learn) + LogLearningEventUseCase"
```

---

## Task 10: PairAndLearnUiState + PairAndLearnViewModel (TDD)

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/PairAndLearnUiState.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/PairAndLearnViewModel.kt`
- Create: `app/src/test/kotlin/com/wearaware/app/ui/viewmodel/PairAndLearnViewModelTest.kt`

- [ ] **Step 1: Write UiState and effect types**

`PairAndLearnUiState.kt`:
```kotlin
package com.wearaware.app.ui.viewmodel

import android.content.IntentSender
import com.wearaware.app.domain.model.KnownTargetSignature
import com.wearaware.app.domain.model.PairedLearningEvent

enum class PairingFlowState {
    IDLE,
    CDM_SCANNING,
    CDM_WAITING_SELECTION,
    CDM_ASSOCIATED,
    GATT_CONNECTING,
    GATT_DISCOVERING,
    LEARNING,
    COMPLETED,
    FAILED
}

data class PairAndLearnUiState(
    val flowState: PairingFlowState = PairingFlowState.IDLE,
    val statusMessage: String = "",
    val sessionId: String? = null,
    val recentEvents: List<PairedLearningEvent> = emptyList(),
    val existingSignature: KnownTargetSignature? = null,
    val errorMessage: String? = null
)

sealed class PairAndLearnEffect {
    data class LaunchCdmPicker(val intentSender: IntentSender) : PairAndLearnEffect()
}
```

- [ ] **Step 2: Write the failing ViewModel tests**

`PairAndLearnViewModelTest.kt`:
```kotlin
package com.wearaware.app.ui.viewmodel

import android.app.Activity
import androidx.activity.result.ActivityResult
import com.wearaware.app.data.ble.BleGattManager
import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.BleRepository
import com.wearaware.app.domain.repository.KnownTargetRepository
import com.wearaware.app.domain.repository.LearningSessionRepository
import com.wearaware.app.domain.usecase.BuildKnownTargetSignatureUseCase
import com.wearaware.app.domain.usecase.LogLearningEventUseCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairAndLearnViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val knownTargetRepository = mockk<KnownTargetRepository>(relaxed = true)
    private val learningSessionRepository = mockk<LearningSessionRepository>(relaxed = true)
    private val logLearningEvent = mockk<LogLearningEventUseCase>(relaxed = true)
    private val buildKnownTargetSignature = BuildKnownTargetSignatureUseCase()
    private val bleGattManager = mockk<BleGattManager>()
    private val bleRepository = mockk<BleRepository>(relaxed = true)

    private val deviceFlow = MutableStateFlow<List<ObservedDevice>>(emptyList())

    private lateinit var viewModel: PairAndLearnViewModel

    private val testGattResult = GattDiscoveryResult(
        deviceAddress = "AA:BB:CC:DD:EE:FF",
        serviceUuids = listOf("0000180a-0000-1000-8000-00805f9b34fb"),
        characteristicUuids = emptyMap(),
        discoveredAt = 1000L
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { knownTargetRepository.load() } returns null
        every { bleRepository.observedDevices } returns deviceFlow
        viewModel = PairAndLearnViewModel(
            knownTargetRepository = knownTargetRepository,
            learningSessionRepository = learningSessionRepository,
            logLearningEvent = logLearningEvent,
            buildKnownTargetSignature = buildKnownTargetSignature,
            bleGattManager = bleGattManager,
            bleRepository = bleRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is IDLE with no error`() {
        assertEquals(PairingFlowState.IDLE, viewModel.uiState.value.flowState)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `init loads existing signature`() {
        val sig = KnownTargetSignature(
            displayName = "My Meta Glasses", savedAt = 1000L, fingerprintId = "fp",
            manufacturerIds = emptyList(), manufacturerDataPrefixes = emptyList(),
            serviceUuids = emptyList(), gattServiceUuids = emptyList(), behaviorProfile = null
        )
        every { knownTargetRepository.load() } returns sig
        val vm = PairAndLearnViewModel(
            knownTargetRepository, learningSessionRepository, logLearningEvent,
            buildKnownTargetSignature, bleGattManager, bleRepository
        )
        assertEquals(sig, vm.uiState.value.existingSignature)
    }

    @Test
    fun `onCdmResult with RESULT_CANCELLED transitions to FAILED`() = runTest {
        viewModel.onCdmResult(ActivityResult(Activity.RESULT_CANCELED, null))
        advanceUntilIdle()
        assertEquals(PairingFlowState.FAILED, viewModel.uiState.value.flowState)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `successful GATT flow transitions to COMPLETED and saves signature`() = runTest {
        coEvery { bleGattManager.connectAndDiscover("AA:BB:CC:DD:EE:FF") } returns testGattResult
        coEvery { learningSessionRepository.createSession(any(), any()) } just Runs
        coEvery { learningSessionRepository.updateStatus(any(), any()) } just Runs
        coEvery { learningSessionRepository.updateDeviceAddress(any(), any()) } just Runs
        coEvery { logLearningEvent(any(), any(), any()) } returns PairedLearningEvent(
            1L, "session", LearningEventType.SESSION_STARTED, 1000L, null
        )

        viewModel.onCdmAssociated("AA:BB:CC:DD:EE:FF", "session-1")
        advanceUntilIdle()

        assertEquals(PairingFlowState.COMPLETED, viewModel.uiState.value.flowState)
        verify { knownTargetRepository.save(any()) }
    }

    @Test
    fun `GATT failure transitions to FAILED with error message`() = runTest {
        coEvery { bleGattManager.connectAndDiscover(any()) } throws Exception("Connection timeout")
        coEvery { learningSessionRepository.createSession(any(), any()) } just Runs
        coEvery { learningSessionRepository.updateStatus(any(), any()) } just Runs
        coEvery { learningSessionRepository.updateDeviceAddress(any(), any()) } just Runs
        coEvery { logLearningEvent(any(), any(), any()) } returns PairedLearningEvent(
            1L, "session", LearningEventType.GATT_FAILED, 1000L, null
        )

        viewModel.onCdmAssociated("AA:BB:CC:DD:EE:FF", "session-1")
        advanceUntilIdle()

        assertEquals(PairingFlowState.FAILED, viewModel.uiState.value.flowState)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `clearProfile removes signature from repository and resets state`() {
        viewModel.clearProfile()
        verify { knownTargetRepository.clear() }
        assertNull(viewModel.uiState.value.existingSignature)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```bash
./gradlew :app:test --tests "com.wearaware.app.ui.viewmodel.PairAndLearnViewModelTest" 2>&1 | tail -20
```
Expected: FAIL — class not found.

- [ ] **Step 4: Write the ViewModel**

`PairAndLearnViewModel.kt`:
```kotlin
package com.wearaware.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.Activity
import androidx.activity.result.ActivityResult
import com.wearaware.app.data.ble.BleGattManager
import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.repository.BleRepository
import com.wearaware.app.domain.repository.KnownTargetRepository
import com.wearaware.app.domain.repository.LearningSessionRepository
import com.wearaware.app.domain.usecase.BuildKnownTargetSignatureUseCase
import com.wearaware.app.domain.usecase.LogLearningEventUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PairAndLearnViewModel @Inject constructor(
    private val knownTargetRepository: KnownTargetRepository,
    private val learningSessionRepository: LearningSessionRepository,
    private val logLearningEvent: LogLearningEventUseCase,
    private val buildKnownTargetSignature: BuildKnownTargetSignatureUseCase,
    private val bleGattManager: BleGattManager,
    private val bleRepository: BleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairAndLearnUiState())
    val uiState: StateFlow<PairAndLearnUiState> = _uiState.asStateFlow()

    private val _effects = Channel<PairAndLearnEffect>(Channel.BUFFERED)
    val effects: Flow<PairAndLearnEffect> = _effects.receiveAsFlow()

    init {
        val sig = knownTargetRepository.load()
        _uiState.update { it.copy(existingSignature = sig) }
    }

    /**
     * Called from Screen when CDM device picker returns. Handles both cancel and success.
     * On success, calls onCdmAssociated with the selected device's MAC address.
     */
    fun onCdmResult(result: ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK) {
            viewModelScope.launch {
                val sessionId = _uiState.value.sessionId
                if (sessionId != null) {
                    logLearningEvent(sessionId, LearningEventType.CDM_FAILED, "User cancelled device picker")
                    learningSessionRepository.updateStatus(sessionId, LearningSessionStatus.ABANDONED)
                }
                _uiState.update { it.copy(flowState = PairingFlowState.FAILED, errorMessage = "Pairing cancelled") }
            }
            return
        }
        val device = result.data?.getParcelableExtra<android.bluetooth.BluetoothDevice>(
            android.companion.CompanionDeviceManager.EXTRA_DEVICE
        )
        val address = device?.address
        if (address == null) {
            _uiState.update { it.copy(flowState = PairingFlowState.FAILED, errorMessage = "No device address received") }
            return
        }
        val sessionId = _uiState.value.sessionId ?: return
        onCdmAssociated(address, sessionId)
    }

    /**
     * Exposed for testing. Starts GATT connect + learn flow for a known device address.
     */
    fun onCdmAssociated(deviceAddress: String, sessionId: String) {
        viewModelScope.launch {
            try {
                logLearningEvent(sessionId, LearningEventType.CDM_ASSOCIATED, "Device selected: $deviceAddress")
                learningSessionRepository.updateDeviceAddress(sessionId, deviceAddress)

                _uiState.update { it.copy(flowState = PairingFlowState.GATT_CONNECTING, statusMessage = "Connecting to device...") }
                logLearningEvent(sessionId, LearningEventType.GATT_CONNECTING, "Initiating GATT connection")

                _uiState.update { it.copy(flowState = PairingFlowState.GATT_DISCOVERING, statusMessage = "Discovering services...") }
                val gattResult = bleGattManager.connectAndDiscover(deviceAddress)

                logLearningEvent(sessionId, LearningEventType.GATT_SERVICES_DISCOVERED,
                    "Discovered ${gattResult.serviceUuids.size} GATT services")
                logLearningEvent(sessionId, LearningEventType.GATT_DISCONNECTED, "GATT disconnected after discovery")

                _uiState.update { it.copy(flowState = PairingFlowState.LEARNING, statusMessage = "Building glasses profile...") }

                // Find the device in current BLE scan results by MAC address
                val observedDevice = bleRepository.observedDevices.value
                    .firstOrNull { it.macAddress == deviceAddress }

                val signature = buildKnownTargetSignature(observedDevice, gattResult)
                knownTargetRepository.save(signature)

                logLearningEvent(sessionId, LearningEventType.SIGNATURE_BUILT, "Signature saved: ${signature.fingerprintId}")
                logLearningEvent(sessionId, LearningEventType.SESSION_COMPLETED, "Learning session completed")
                learningSessionRepository.updateStatus(sessionId, LearningSessionStatus.COMPLETED)

                _uiState.update {
                    it.copy(
                        flowState = PairingFlowState.COMPLETED,
                        statusMessage = "Glasses profile saved!",
                        existingSignature = signature
                    )
                }
            } catch (e: Exception) {
                logLearningEvent(sessionId, LearningEventType.GATT_FAILED, "Error: ${e.message}")
                learningSessionRepository.updateStatus(sessionId, LearningSessionStatus.FAILED)
                _uiState.update {
                    it.copy(flowState = PairingFlowState.FAILED, errorMessage = "Connection failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Initiates the CDM association flow. Emits LaunchCdmPicker effect when the device
     * chooser IntentSender is ready.
     */
    fun startPairing(companionDeviceManager: android.companion.CompanionDeviceManager) {
        val sessionId = UUID.randomUUID().toString()
        viewModelScope.launch {
            learningSessionRepository.createSession(sessionId, System.currentTimeMillis())
            logLearningEvent(sessionId, LearningEventType.SESSION_STARTED, "Pairing session initiated")
            _uiState.update { it.copy(flowState = PairingFlowState.CDM_SCANNING, statusMessage = "Scanning for BLE devices...", sessionId = sessionId) }
            logLearningEvent(sessionId, LearningEventType.CDM_SCANNING, "CDM scanning started")

            val request = android.companion.AssociationRequest.Builder()
                .addDeviceFilter(
                    android.companion.BluetoothLeDeviceFilter.Builder().build()
                )
                .setSingleDevice(false)
                .build()

            companionDeviceManager.associate(request, object : android.companion.CompanionDeviceManager.Callback() {
                override fun onDeviceFound(chooserLauncher: android.content.IntentSender) {
                    viewModelScope.launch {
                        _uiState.update { it.copy(flowState = PairingFlowState.CDM_WAITING_SELECTION, statusMessage = "Select your glasses from the list") }
                        logLearningEvent(sessionId, LearningEventType.CDM_WAITING_SELECTION, "Device picker displayed")
                        _effects.send(PairAndLearnEffect.LaunchCdmPicker(chooserLauncher))
                    }
                }
                override fun onFailure(error: CharSequence?) {
                    viewModelScope.launch {
                        logLearningEvent(sessionId, LearningEventType.CDM_FAILED, "CDM failed: $error")
                        learningSessionRepository.updateStatus(sessionId, LearningSessionStatus.FAILED)
                        _uiState.update { it.copy(flowState = PairingFlowState.FAILED, errorMessage = "Scan failed: $error") }
                    }
                }
            }, null)
        }
    }

    fun clearProfile() {
        knownTargetRepository.clear()
        _uiState.update { it.copy(existingSignature = null) }
    }

    fun resetToIdle() {
        _uiState.update { it.copy(flowState = PairingFlowState.IDLE, errorMessage = null, statusMessage = "") }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew :app:test --tests "com.wearaware.app.ui.viewmodel.PairAndLearnViewModelTest" 2>&1 | tail -20
```
Expected: All tests PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/viewmodel/PairAndLearnUiState.kt \
        app/src/main/kotlin/com/wearaware/app/ui/viewmodel/PairAndLearnViewModel.kt \
        app/src/test/kotlin/com/wearaware/app/ui/viewmodel/PairAndLearnViewModelTest.kt
git commit -m "feat: PairAndLearnViewModel — CDM + GATT orchestration with state machine"
```

---

## Task 11: PairAndLearnScreen

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/ui/screens/PairAndLearnScreen.kt`

- [ ] **Step 1: Write the screen**

`PairAndLearnScreen.kt`:
```kotlin
package com.wearaware.app.ui.screens

import android.companion.CompanionDeviceManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wearaware.app.ui.viewmodel.PairAndLearnEffect
import com.wearaware.app.ui.viewmodel.PairAndLearnViewModel
import com.wearaware.app.ui.viewmodel.PairingFlowState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairAndLearnScreen(
    onBack: () -> Unit,
    onViewLog: () -> Unit,
    viewModel: PairAndLearnViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // CDM device picker launcher — handles the Activity result from the system chooser
    val cdmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onCdmResult(result)
    }

    // Collect effects (CDM IntentSender)
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PairAndLearnEffect.LaunchCdmPicker -> {
                    cdmLauncher.launch(IntentSenderRequest.Builder(effect.intentSender).build())
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair & Learn My Glasses") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onViewLog) {
                        Text("History", style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Existing profile banner
            val existing = uiState.existingSignature
            if (existing != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Learned device profile active", style = MaterialTheme.typography.titleSmall)
                        Text(existing.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Saved ${java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(existing.savedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (existing.gattServiceUuids.isNotEmpty()) {
                            Text(
                                "GATT services: ${existing.gattServiceUuids.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { viewModel.clearProfile() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Remove profile") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Flow state display
            when (uiState.flowState) {
                PairingFlowState.IDLE -> {
                    Text(
                        "Pair your glasses so WearAware can recognize them on future scans.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val cdm = context.getSystemService(CompanionDeviceManager::class.java)
                            viewModel.startPairing(cdm)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (existing != null) "Pair new device (replaces current)" else "Pair & Learn My Glasses")
                    }
                }

                PairingFlowState.CDM_SCANNING,
                PairingFlowState.CDM_WAITING_SELECTION,
                PairingFlowState.CDM_ASSOCIATED,
                PairingFlowState.GATT_CONNECTING,
                PairingFlowState.GATT_DISCOVERING,
                PairingFlowState.LEARNING -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(uiState.statusMessage, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stepHintFor(uiState.flowState),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                PairingFlowState.COMPLETED -> {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text("Profile saved!", style = MaterialTheme.typography.titleMedium)
                    Text(
                        uiState.existingSignature?.displayName ?: "",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Done")
                    }
                    TextButton(onClick = { viewModel.resetToIdle() }) {
                        Text("Pair a different device")
                    }
                }

                PairingFlowState.FAILED -> {
                    Text(
                        uiState.errorMessage ?: "Something went wrong",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.resetToIdle() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Try Again")
                    }
                }
            }
        }
    }
}

private fun stepHintFor(state: PairingFlowState): String = when (state) {
    PairingFlowState.CDM_SCANNING -> "Searching for nearby BLE devices..."
    PairingFlowState.CDM_WAITING_SELECTION -> "Select your glasses from the system dialog"
    PairingFlowState.CDM_ASSOCIATED -> "Device selected"
    PairingFlowState.GATT_CONNECTING -> "Opening Bluetooth connection..."
    PairingFlowState.GATT_DISCOVERING -> "Reading device capabilities..."
    PairingFlowState.LEARNING -> "Building your glasses profile..."
    else -> ""
}
```

- [ ] **Step 2: Verify compile**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: Compiles cleanly. (Note: `Icons.Default.CheckCircle` requires `material-icons-extended` dependency — if missing, replace with a `Text("✓")` placeholder.)

If the CheckCircle icon fails to compile, replace:
```kotlin
Icon(
    imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.primary,
    modifier = Modifier.size(48.dp)
)
```
with:
```kotlin
Text("✓", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/screens/PairAndLearnScreen.kt
git commit -m "feat: PairAndLearnScreen — CDM dialog + state machine UI"
```

---

## Task 12: LearningLogScreen + LearningSessionDetailScreen

**Files:**
- Create: `app/src/main/kotlin/com/wearaware/app/ui/screens/LearningLogScreen.kt`
- Create: `app/src/main/kotlin/com/wearaware/app/ui/screens/LearningSessionDetailScreen.kt`

- [ ] **Step 1: Write LearningLogScreen**

`LearningLogScreen.kt`:
```kotlin
package com.wearaware.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wearaware.app.domain.model.LearningSessionStatus
import com.wearaware.app.domain.model.PairedLearningSession
import com.wearaware.app.ui.viewmodel.LearningLogViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningLogScreen(
    onBack: () -> Unit,
    onSessionClick: (String) -> Unit,
    viewModel: LearningLogViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Learning History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("No learning sessions yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions) { session ->
                    SessionCard(session = session, onClick = { onSessionClick(session.sessionId) })
                }
            }
        }
    }
}

@Composable
private fun SessionCard(session: PairedLearningSession, onClick: () -> Unit) {
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    val statusColor = when (session.status) {
        LearningSessionStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        LearningSessionStatus.FAILED -> MaterialTheme.colorScheme.error
        LearningSessionStatus.ABANDONED -> MaterialTheme.colorScheme.onSurfaceVariant
        LearningSessionStatus.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(fmt.format(Date(session.startedAt)), style = MaterialTheme.typography.bodyMedium)
                Text(session.status.name, style = MaterialTheme.typography.labelSmall, color = statusColor)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("${session.eventCount} events", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            session.fingerprintId?.let {
                Text("Fingerprint: $it", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
```

Also create a minimal `LearningLogViewModel.kt`:
```kotlin
package com.wearaware.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wearaware.app.domain.model.PairedLearningSession
import com.wearaware.app.domain.repository.LearningSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LearningLogViewModel @Inject constructor(
    private val learningSessionRepository: LearningSessionRepository
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<PairedLearningSession>>(emptyList())
    val sessions: StateFlow<List<PairedLearningSession>> = _sessions

    init {
        viewModelScope.launch {
            _sessions.value = learningSessionRepository.getAllSessions()
        }
    }
}
```

- [ ] **Step 2: Write LearningSessionDetailScreen**

`LearningSessionDetailScreen.kt`:
```kotlin
package com.wearaware.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wearaware.app.domain.model.LearningEventType
import com.wearaware.app.domain.model.PairedLearningEvent
import com.wearaware.app.ui.viewmodel.LearningSessionDetailViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningSessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    viewModel: LearningSessionDetailViewModel = hiltViewModel()
) {
    val events by viewModel.events.collectAsState()
    val session by viewModel.session.collectAsState()

    LaunchedEffect(sessionId) {
        viewModel.load(sessionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            session?.let {
                item {
                    Text("Status: ${it.status.name}", style = MaterialTheme.typography.titleSmall)
                    it.fingerprintId?.let { fp ->
                        Text("Fingerprint: $fp", style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            items(events) { event ->
                EventRow(event = event)
            }
        }
    }
}

@Composable
private fun EventRow(event: PairedLearningEvent) {
    val fmt = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val color = when (event.eventType) {
        LearningEventType.GATT_FAILED, LearningEventType.CDM_FAILED ->
            MaterialTheme.colorScheme.error
        LearningEventType.SIGNATURE_BUILT, LearningEventType.SESSION_COMPLETED ->
            MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(fmt.format(Date(event.occurredAt)), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(event.eventType.name.replace('_', ' '), style = MaterialTheme.typography.bodySmall, color = color)
            event.detail?.let {
                Text(it, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
```

Also create `LearningSessionDetailViewModel.kt`:
```kotlin
package com.wearaware.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wearaware.app.domain.model.PairedLearningEvent
import com.wearaware.app.domain.model.PairedLearningSession
import com.wearaware.app.domain.repository.LearningSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LearningSessionDetailViewModel @Inject constructor(
    private val repository: LearningSessionRepository
) : ViewModel() {

    private val _events = MutableStateFlow<List<PairedLearningEvent>>(emptyList())
    val events: StateFlow<List<PairedLearningEvent>> = _events

    private val _session = MutableStateFlow<PairedLearningSession?>(null)
    val session: StateFlow<PairedLearningSession?> = _session

    fun load(sessionId: String) {
        viewModelScope.launch {
            _session.value = repository.getSession(sessionId)
            _events.value = repository.getEventsForSession(sessionId)
        }
    }
}
```

- [ ] **Step 3: Verify compile**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: Compiles cleanly.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/screens/LearningLogScreen.kt \
        app/src/main/kotlin/com/wearaware/app/ui/screens/LearningSessionDetailScreen.kt \
        app/src/main/kotlin/com/wearaware/app/ui/viewmodel/LearningLogViewModel.kt \
        app/src/main/kotlin/com/wearaware/app/ui/viewmodel/LearningSessionDetailViewModel.kt
git commit -m "feat: LearningLogScreen + LearningSessionDetailScreen"
```

---

## Task 13: Navigation wiring + ScanScreen "Pair & Learn" action

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/navigation/NavGraph.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/screens/ScanScreen.kt`

- [ ] **Step 1: Add new routes to NavGraph.kt**

In `NavGraph.kt`, add to the `sealed class Screen` block (after `CaptureDeviceDetail`):
```kotlin
    object PairAndLearn : Screen("pair_and_learn")
    object LearningLog : Screen("learning_log")
    object LearningSessionDetail : Screen("learning_session/{sessionId}") {
        fun routeFor(sessionId: String) = "learning_session/$sessionId"
    }
```

Add composable blocks before the closing `}` of `NavHost`:
```kotlin
        composable(Screen.PairAndLearn.route) {
            PairAndLearnScreen(
                onBack = { navController.popBackStack() },
                onViewLog = { navController.navigate(Screen.LearningLog.route) }
            )
        }
        composable(Screen.LearningLog.route) {
            LearningLogScreen(
                onBack = { navController.popBackStack() },
                onSessionClick = { sessionId ->
                    navController.navigate(Screen.LearningSessionDetail.routeFor(sessionId))
                }
            )
        }
        composable(Screen.LearningSessionDetail.route) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            LearningSessionDetailScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() }
            )
        }
```

Also add the missing imports:
```kotlin
import com.wearaware.app.ui.screens.LearningLogScreen
import com.wearaware.app.ui.screens.LearningSessionDetailScreen
import com.wearaware.app.ui.screens.PairAndLearnScreen
```

Update the `ScanScreen` composable call to pass `onPairAndLearnClick`:
```kotlin
        composable(Screen.Scan.route) {
            ScanScreen(
                onDeviceClick = { deviceId ->
                    navController.navigate(Screen.DeviceDetail.routeFor(deviceId))
                },
                onCaptureClick = {
                    navController.navigate(Screen.Capture.route)
                },
                onPairAndLearnClick = {
                    navController.navigate(Screen.PairAndLearn.route)
                }
            )
        }
```

- [ ] **Step 2: Update ScanScreen to add the "Pair & Learn" action**

In `ScanScreen.kt`, update the function signature:
```kotlin
fun ScanScreen(
    onDeviceClick: (String) -> Unit,
    onCaptureClick: () -> Unit,
    onPairAndLearnClick: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
)
```

In the `TopAppBar` actions, add the button before the "Compare" button:
```kotlin
                actions = {
                    TextButton(onClick = onPairAndLearnClick) {
                        Text(
                            text = "Pair & Learn",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    TextButton(onClick = onCaptureClick) {
                        Text(
                            text = "Compare",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    // ... rest of existing actions unchanged
```

- [ ] **Step 3: Verify compile**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: Compiles cleanly.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/navigation/NavGraph.kt \
        app/src/main/kotlin/com/wearaware/app/ui/screens/ScanScreen.kt
git commit -m "feat: navigation wiring — PairAndLearn, LearningLog, LearningSessionDetail routes"
```

---

## Task 14: Migrate CaptureViewModel + CaptureUiState

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/CaptureUiState.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/CaptureViewModel.kt`

- [ ] **Step 1: Update CaptureUiState.kt**

Replace the entire file:
```kotlin
package com.wearaware.app.ui.viewmodel

import com.wearaware.app.domain.model.CaptureSession
import com.wearaware.app.domain.model.CaptureState
import com.wearaware.app.domain.model.CompareMatchResult
import com.wearaware.app.domain.model.KnownMatchConfidence
import com.wearaware.app.domain.model.KnownTargetMatchResult
import com.wearaware.app.domain.model.KnownTargetSignature

data class CaptureUiState(
    val baselineCaptureState: CaptureState = CaptureState.IDLE,
    val targetCaptureState: CaptureState = CaptureState.IDLE,
    val baseline: CaptureSession? = null,
    val target: CaptureSession? = null,
    val liveAccumulatedCount: Int = 0,
    val compareResults: List<CompareMatchResult> = emptyList(),
    val captureStartedAt: Long = 0L,
    val isBleScanningActive: Boolean = false,
    val isBleAvailable: Boolean = true,
    val knownTargetSignature: KnownTargetSignature? = null,
    val learnedMatchResults: Map<String, KnownTargetMatchResult> = emptyMap(),
    val learnSaveConfirmation: String? = null,
)
```

- [ ] **Step 2: Update CaptureViewModel.kt**

Replace all references to old types. The constructor changes from:
```kotlin
// Old imports to remove:
import com.wearaware.app.domain.repository.LearnedSignatureRepository
import com.wearaware.app.domain.usecase.ClearLearnedSignatureUseCase
import com.wearaware.app.domain.usecase.MatchLearnedSignatureUseCase
import com.wearaware.app.domain.usecase.SaveLearnedSignatureUseCase
import com.wearaware.app.domain.model.LearnedMatchInput
import com.wearaware.app.domain.model.LearnedMatchResult
```

To:
```kotlin
import com.wearaware.app.domain.repository.KnownTargetRepository
import com.wearaware.app.domain.usecase.MatchKnownTargetSignatureUseCase
import com.wearaware.app.domain.usecase.SaveKnownTargetFromCaptureUseCase
import com.wearaware.app.domain.model.KnownTargetMatchInput
import com.wearaware.app.domain.model.KnownTargetMatchResult
import com.wearaware.app.domain.usecase.extractPrefixesFromSummary
```

Constructor parameter changes:
```kotlin
// Replace:
private val saveLearnedSignature: SaveLearnedSignatureUseCase,
private val matchLearnedSignature: MatchLearnedSignatureUseCase,
private val clearLearnedSignature: ClearLearnedSignatureUseCase,
private val learnedSignatureRepository: LearnedSignatureRepository,

// With:
private val saveKnownTarget: SaveKnownTargetFromCaptureUseCase,
private val matchKnownTarget: MatchKnownTargetSignatureUseCase,
private val knownTargetRepository: KnownTargetRepository,
```

In the `init` block, change:
```kotlin
// Replace:
val sig = learnedSignatureRepository.load()
_uiState.update {
    it.copy(
        ...
        learnedSignature = sig
    )
}

// With:
val sig = knownTargetRepository.load()
_uiState.update {
    it.copy(
        ...
        knownTargetSignature = sig
    )
}
```

In `learnDevice()`, change:
```kotlin
// Replace:
val sig = saveLearnedSignature(device)
val matchResults = computeLearnedMatchesForCompare(sig, _uiState.value.compareResults)
_uiState.update {
    it.copy(
        learnedSignature = sig,
        learnedMatchResults = matchResults,
        learnSaveConfirmation = "Saved as My Meta Glasses"
    )
}

// With:
val sig = saveKnownTarget(device)
val matchResults = computeKnownTargetMatchesForCompare(sig, _uiState.value.compareResults)
_uiState.update {
    it.copy(
        knownTargetSignature = sig,
        learnedMatchResults = matchResults,
        learnSaveConfirmation = "Saved as My Meta Glasses"
    )
}
```

In `clearLearnedDevice()`, change:
```kotlin
// Replace:
fun clearLearnedDevice() {
    clearLearnedSignature()
    _uiState.update {
        it.copy(
            learnedSignature = null,
            learnedMatchResults = emptyMap()
        )
    }
}

// With:
fun clearLearnedDevice() {
    knownTargetRepository.clear()
    _uiState.update {
        it.copy(
            knownTargetSignature = null,
            learnedMatchResults = emptyMap()
        )
    }
}
```

In `runCompare()`, change:
```kotlin
// Replace:
val sig = _uiState.value.learnedSignature
val matchResults = if (sig != null) {
    computeLearnedMatchesForCompare(sig, results)
} else { emptyMap() }

// With:
val sig = _uiState.value.knownTargetSignature
val matchResults = if (sig != null) {
    computeKnownTargetMatchesForCompare(sig, results)
} else { emptyMap() }
```

Rename `computeLearnedMatchesForCompare` to `computeKnownTargetMatchesForCompare` and update its signature + body:
```kotlin
private fun computeKnownTargetMatchesForCompare(
    signature: KnownTargetSignature,
    compareResults: List<CompareMatchResult>
): Map<String, KnownTargetMatchResult> {
    return compareResults.associate { result ->
        val device = result.capturedDevice
        val input = KnownTargetMatchInput(
            fingerprintId = device.fingerprintId,
            manufacturerIds = device.manufacturerIds,
            manufacturerDataPrefixes = extractPrefixesFromSummary(device.manufacturerDataSummary),
            serviceUuids = device.serviceUuids,
            gattServiceUuids = emptyList(),
            averageRssi = device.averageRssi,
            seenCount = device.seenCount,
            visibleAtStop = device.visibleAtStop,
            connectable = false
        )
        device.fingerprintId to matchKnownTarget(input, signature)
    }
}
```

- [ ] **Step 3: Verify compile**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: Compiles cleanly. (Old test files for the old types will also need updating — that's Task 17.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/viewmodel/CaptureUiState.kt \
        app/src/main/kotlin/com/wearaware/app/ui/viewmodel/CaptureViewModel.kt
git commit -m "feat: migrate CaptureViewModel + CaptureUiState to KnownTargetSignature"
```

---

## Task 15: Migrate ScanViewModel + ScanUiState

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanUiState.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanViewModel.kt`

- [ ] **Step 1: Update ScanUiState.kt**

Replace `LearnedDeviceSignature` → `KnownTargetSignature`, `LearnedMatchResult` → `KnownTargetMatchResult`:
```kotlin
package com.wearaware.app.ui.viewmodel

import com.wearaware.app.domain.model.KnownTargetMatchResult
import com.wearaware.app.domain.model.KnownTargetSignature
import com.wearaware.app.domain.model.MatchConfidence
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.PersistenceAlert
import com.wearaware.app.domain.model.ScanFilter
import com.wearaware.app.domain.model.TargetMatchResult

data class ScanUiState(
    val scanState: ScanState = ScanState.STOPPED,
    val devices: List<ObservedDevice> = emptyList(),
    val activeAlert: PersistenceAlert? = null,
    val appVersion: String = "",
    val ruleSetVersion: String = "",
    val ruleSetHash: String = "",
    val focusMode: Boolean = false,
    val debugMode: Boolean = false,
    val deviceMatchScores: Map<String, TargetMatchResult> = emptyMap(),
    val activeFilter: ScanFilter = ScanFilter.ALL,
    val knownTargetSignature: KnownTargetSignature? = null,
    val learnedMatchResults: Map<String, KnownTargetMatchResult> = emptyMap(),
) {
    val bestMatch: Pair<ObservedDevice, TargetMatchResult>?
        get() {
            val top = deviceMatchScores.values.firstOrNull {
                it.isTopCandidate &&
                    (it.confidence == MatchConfidence.HIGH || it.confidence == MatchConfidence.MEDIUM)
            } ?: return null
            val device = devices.find { it.id == top.deviceId } ?: return null
            return device to top
        }

    val sortedDevices: List<ObservedDevice>
        get() = if (focusMode && deviceMatchScores.isNotEmpty()) {
            devices.sortedByDescending { deviceMatchScores[it.id]?.score ?: 0 }
        } else {
            devices
        }

    val filteredDevices: List<ObservedDevice>
        get() = sortedDevices.filter { activeFilter.matches(it) }
}

enum class ScanState {
    STOPPED,
    SCANNING,
    BLUETOOTH_UNAVAILABLE,
    PERMISSIONS_REQUIRED
}
```

- [ ] **Step 2: Update ScanViewModel.kt**

Replace all old learned-signature references:
```kotlin
// Remove these imports:
import com.wearaware.app.domain.repository.LearnedSignatureRepository
import com.wearaware.app.domain.usecase.MatchLearnedSignatureUseCase
import com.wearaware.app.domain.model.LearnedMatchInput
import com.wearaware.app.domain.model.LearnedMatchResult

// Add these imports:
import com.wearaware.app.domain.repository.KnownTargetRepository
import com.wearaware.app.domain.usecase.MatchKnownTargetSignatureUseCase
import com.wearaware.app.domain.model.KnownTargetMatchInput
import com.wearaware.app.domain.model.KnownTargetMatchResult
```

Constructor parameter changes:
```kotlin
// Replace:
private val matchLearnedSignature: MatchLearnedSignatureUseCase,
private val learnedSignatureRepository: LearnedSignatureRepository,

// With:
private val matchKnownTarget: MatchKnownTargetSignatureUseCase,
private val knownTargetRepository: KnownTargetRepository,
```

In `init` block, change:
```kotlin
// Replace:
val sig = learnedSignatureRepository.load()
if (sig != null) {
    val learnedMatches = computeLearnedMatches(_uiState.value.devices, sig)
    _uiState.update { it.copy(learnedSignature = sig, learnedMatchResults = learnedMatches) }
}

// With:
val sig = knownTargetRepository.load()
if (sig != null) {
    val learnedMatches = computeKnownTargetMatches(_uiState.value.devices, sig)
    _uiState.update { it.copy(knownTargetSignature = sig, learnedMatchResults = learnedMatches) }
}
```

In `reloadLearnedSignature()`, change:
```kotlin
fun reloadLearnedSignature() {
    val sig = knownTargetRepository.load()
    val learnedMatches = computeKnownTargetMatches(_uiState.value.devices, sig)
    _uiState.update { it.copy(knownTargetSignature = sig, learnedMatchResults = learnedMatches) }
}
```

Rename `computeLearnedMatches` to `computeKnownTargetMatches` and update the body:
```kotlin
private fun computeKnownTargetMatches(
    devices: List<ObservedDevice>,
    sig: KnownTargetSignature?
): Map<String, KnownTargetMatchResult> {
    if (sig == null) return emptyMap()
    return devices.associate { device ->
        val input = KnownTargetMatchInput(
            fingerprintId = device.id,
            manufacturerIds = device.fingerprint?.manufacturerIds ?: emptyList(),
            manufacturerDataPrefixes = extractPrefixesFromFingerprintMap(
                device.fingerprint?.manufacturerDataHex
            ),
            serviceUuids = device.fingerprint?.serviceUuids ?: emptyList(),
            gattServiceUuids = emptyList(),
            averageRssi = device.averagedRssi,
            seenCount = device.seenCount,
            visibleAtStop = false,
            connectable = device.rawBleData?.isConnectable ?: false
        )
        device.id to matchKnownTarget(input, sig)
    }
}
```

In `processDeviceUpdate()`, change:
```kotlin
val learnedMatches = computeKnownTargetMatches(devices, _uiState.value.knownTargetSignature)
```

In `stopScanning()`, change:
```kotlin
learnedMatchResults = emptyMap()
```
(This field name doesn't change — keep `learnedMatchResults` as the key in `ScanUiState`.)

- [ ] **Step 3: Verify compile**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: Compiles cleanly.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanUiState.kt \
        app/src/main/kotlin/com/wearaware/app/ui/viewmodel/ScanViewModel.kt
git commit -m "feat: migrate ScanViewModel + ScanUiState to KnownTargetSignature"
```

---

## Task 16: Migrate UI components

**Files:**
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/components/DeviceCard.kt`
- Modify: `app/src/main/kotlin/com/wearawesome/app/ui/screens/CapturedDeviceDetailScreen.kt`
- Modify: `app/src/main/kotlin/com/wearaware/app/ui/screens/ScanScreen.kt` (learned device chip)

- [ ] **Step 1: Update DeviceCard.kt**

Replace the `learnedMatchResult` parameter type and all usages of `LearnedMatchResult`/`LearnedConfidence`:
```kotlin
// Replace:
learnedMatchResult: com.wearaware.app.domain.model.LearnedMatchResult? = null

// With:
learnedMatchResult: com.wearaware.app.domain.model.KnownTargetMatchResult? = null
```

Replace the label logic:
```kotlin
// Replace:
val learnedLabel: String? = when (learnedMatchResult?.confidence) {
    com.wearaware.app.domain.model.LearnedConfidence.STRONG ->
        learnedMatchResult.signature.displayName
    com.wearaware.app.domain.model.LearnedConfidence.POSSIBLE ->
        "Possible match to your glasses"
    else -> null
}

// With:
val learnedLabel: String? = when (learnedMatchResult?.confidence) {
    com.wearaware.app.domain.model.KnownMatchConfidence.STRONG ->
        learnedMatchResult.signature.displayName
    com.wearaware.app.domain.model.KnownMatchConfidence.POSSIBLE ->
        "Possible match to your glasses"
    else -> null  // WEAK and NONE get no label override
}
```

- [ ] **Step 2: Update CapturedDeviceDetailScreen.kt**

1. Change `uiState.learnedSignature` → `uiState.knownTargetSignature` (two occurrences: the "Learn this device" button check and the debug section).

2. Change the alreadySaved check:
```kotlin
// Replace:
val learnedSignature = uiState.learnedSignature
val alreadySaved = learnedSignature?.fingerprintId == device.fingerprintId

// With:
val knownTargetSignature = uiState.knownTargetSignature
val alreadySaved = knownTargetSignature?.fingerprintId == device.fingerprintId
```

3. Update the "Learned Signature Match" debug section:
```kotlin
// Replace:
val learnedSignatureDebug = uiState.learnedSignature
if (learnedSignatureDebug == null) {
    Text("Learned signature loaded: No", ...)
} else {
    Text("Learned signature loaded: Yes — ${learnedSignatureDebug.displayName}", ...)
    val learnedMatch = uiState.learnedMatchResults[fingerprintId]
    if (learnedMatch == null) {
        Text("Match: not computed (run compare first)", ...)
    } else {
        val confidenceColor = when (learnedMatch.confidence) {
            LearnedConfidence.STRONG -> MaterialTheme.colorScheme.primary
            LearnedConfidence.POSSIBLE -> MaterialTheme.colorScheme.secondary
            LearnedConfidence.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        ...
    }
}

// With:
val knownSig = uiState.knownTargetSignature
if (knownSig == null) {
    Text("Known target profile: None saved", ...)
} else {
    Text("Known target profile: ${knownSig.displayName}", ...)
    val learnedMatch = uiState.learnedMatchResults[fingerprintId]
    if (learnedMatch == null) {
        Text("Match: not computed (run compare first)", ...)
    } else {
        val confidenceColor = when (learnedMatch.confidence) {
            KnownMatchConfidence.STRONG -> MaterialTheme.colorScheme.primary
            KnownMatchConfidence.POSSIBLE -> MaterialTheme.colorScheme.secondary
            KnownMatchConfidence.WEAK -> MaterialTheme.colorScheme.tertiary
            KnownMatchConfidence.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        ...
        // Change LearnedConfidence references to KnownMatchConfidence in the when block
    }
}
```

4. Update the import: remove `LearnedConfidence` import, add `KnownMatchConfidence` import.

5. In `ScanScreen.kt`, update the learned device chip:
```kotlin
// Replace:
val learnedSig = uiState.learnedSignature

// With:
val learnedSig = uiState.knownTargetSignature
```

- [ ] **Step 3: Verify compile**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: Compiles cleanly.

- [ ] **Step 4: Run all tests**

```bash
./gradlew :app:test 2>&1 | tail -30
```
Expected: All existing tests pass. (Some old learned-signature tests will fail — those will be deleted in Task 17.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/wearaware/app/ui/components/DeviceCard.kt \
        app/src/main/kotlin/com/wearaware/app/ui/screens/CapturedDeviceDetailScreen.kt \
        app/src/main/kotlin/com/wearaware/app/ui/screens/ScanScreen.kt
git commit -m "feat: migrate DeviceCard + CapturedDeviceDetailScreen to KnownMatchConfidence"
```

---

## Task 17: Delete old learned-signature system

**Files to delete:**
- `app/src/main/kotlin/com/wearaware/app/domain/model/LearnedDeviceSignature.kt`
- `app/src/main/kotlin/com/wearaware/app/domain/model/LearnedMatchResult.kt`
- `app/src/main/kotlin/com/wearaware/app/domain/model/LearnedMatchInput.kt`
- `app/src/main/kotlin/com/wearaware/app/domain/repository/LearnedSignatureRepository.kt`
- `app/src/main/kotlin/com/wearaware/app/data/repository/LearnedSignatureRepositoryImpl.kt`
- `app/src/main/kotlin/com/wearaware/app/domain/usecase/MatchLearnedSignatureUseCase.kt`
- `app/src/main/kotlin/com/wearaware/app/domain/usecase/SaveLearnedSignatureUseCase.kt`
- `app/src/main/kotlin/com/wearaware/app/domain/usecase/ClearLearnedSignatureUseCase.kt`
- `app/src/test/kotlin/com/wearaware/app/data/repository/LearnedSignatureRepositoryImplTest.kt`
- `app/src/test/kotlin/com/wearaware/app/domain/usecase/MatchLearnedSignatureUseCaseTest.kt`
- `app/src/test/kotlin/com/wearaware/app/domain/usecase/SaveLearnedSignatureUseCaseTest.kt`
- `app/src/test/kotlin/com/wearaware/app/domain/usecase/ClearLearnedSignatureUseCaseTest.kt`
- `app/src/test/kotlin/com/wearaware/app/ui/viewmodel/CaptureViewModelLearnedTest.kt`

- [ ] **Step 1: Delete all old files**

```bash
cd /Users/kingsebruvwiyo/mobile-app/.worktrees/feature/wearaware-v1
git rm app/src/main/kotlin/com/wearaware/app/domain/model/LearnedDeviceSignature.kt
git rm app/src/main/kotlin/com/wearaware/app/domain/model/LearnedMatchResult.kt
git rm app/src/main/kotlin/com/wearaware/app/domain/model/LearnedMatchInput.kt
git rm app/src/main/kotlin/com/wearaware/app/domain/repository/LearnedSignatureRepository.kt
git rm app/src/main/kotlin/com/wearaware/app/data/repository/LearnedSignatureRepositoryImpl.kt
git rm app/src/main/kotlin/com/wearaware/app/domain/usecase/MatchLearnedSignatureUseCase.kt
git rm app/src/main/kotlin/com/wearaware/app/domain/usecase/SaveLearnedSignatureUseCase.kt
git rm app/src/main/kotlin/com/wearaware/app/domain/usecase/ClearLearnedSignatureUseCase.kt
git rm app/src/test/kotlin/com/wearaware/app/data/repository/LearnedSignatureRepositoryImplTest.kt
git rm app/src/test/kotlin/com/wearaware/app/domain/usecase/MatchLearnedSignatureUseCaseTest.kt
git rm app/src/test/kotlin/com/wearaware/app/domain/usecase/SaveLearnedSignatureUseCaseTest.kt
git rm app/src/test/kotlin/com/wearaware/app/domain/usecase/ClearLearnedSignatureUseCaseTest.kt
git rm app/src/test/kotlin/com/wearaware/app/ui/viewmodel/CaptureViewModelLearnedTest.kt
```

- [ ] **Step 2: Verify full compile + all tests pass**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: No compile errors.

```bash
./gradlew :app:test 2>&1 | tail -30
```
Expected: All remaining tests PASS (the deleted test files are gone; no stale references remain).

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: remove old LearnedDeviceSignature system — fully replaced by KnownTargetSignature"
```

---

## Self-Review

**Spec coverage check:**
- ✅ Section 1 (CDM pairing flow, states): `PairAndLearnViewModel.startPairing()` + `PairingFlowState` enum covers all 9 states
- ✅ Section 2 (BLE advertising + GATT): `BuildKnownTargetSignatureUseCase` combines both; `BleGattManager` handles GATT
- ✅ Section 3 (`KnownTargetSignature` model with GATT + behavior profile): Task 1
- ✅ Section 4 (`PairedLearningSession` + `PairedLearningEvent` with 13 event types): Task 1, 4
- ✅ Section 5 (Local storage — signature + sessions + events): Tasks 3, 4
- ✅ Section 6 (`MatchKnownTargetSignatureUseCase` with all scoring): Task 7 (all bonuses and penalties)
- ✅ Section 7 (Label override — STRONG/POSSIBLE/WEAK behavior): `labelOverrideActive` in `KnownTargetMatchResult`; `DeviceCard` updated in Task 16
- ✅ Section 8 (New UI — `PairAndLearnScreen`, log screens, scan updates): Tasks 11, 12, 13, 16
- ✅ Section 9 (Training — quick-learn from capture): `SaveKnownTargetFromCaptureUseCase` in Task 9
- ✅ Section 10 (Optional JSON export): Not implemented — spec says "optional"; excluded per YAGNI
- ✅ Section 11 (Tests): Tasks 3, 7, 8, 10 each have TDD test suites
- ✅ Section 12 (UX wording): `DeviceCard` shows "My Meta Glasses" (STRONG), "Possible match to your glasses" (POSSIBLE), raw label (WEAK/NONE)

**Placeholder scan:** No TBD/TODO present. All code blocks are complete.

**Type consistency:** `KnownTargetSignature` is used throughout Tasks 3–17. `KnownTargetMatchResult` / `KnownMatchConfidence` are consistent in scoring use case (Task 7), ViewModels (Tasks 14–15), and UI components (Task 16). `extractPrefixesFromFingerprintMap` is reused from its existing location in `MatchLearnedSignatureUseCase.kt` — after deletion in Task 17, it must be moved. **Add this step to Task 9:**

In Task 9 (or as a substep of Task 14), move `extractPrefixesFromSummary` and `extractPrefixesFromFingerprintMap` to a shared utility location before deleting `MatchLearnedSignatureUseCase.kt`:

- [ ] **Extraction helpers fix:** After Task 9 and before Task 17, move `extractPrefixesFromSummary` and `extractPrefixesFromFingerprintMap` into `MatchKnownTargetSignatureUseCase.kt` (they are already referenced there via import in the old system), or create `app/src/main/kotlin/com/wearaware/app/domain/usecase/BleDataExtractors.kt`:

```kotlin
package com.wearaware.app.domain.usecase

fun extractPrefixesFromSummary(manufacturerDataSummary: String?): List<String> {
    if (manufacturerDataSummary.isNullOrBlank()) return emptyList()
    return manufacturerDataSummary.split(",").mapNotNull { entry ->
        val colonIdx = entry.indexOf(':')
        if (colonIdx == -1) return@mapNotNull null
        val companyId = entry.substring(0, colonIdx).trim()
        val dataHex = entry.substring(colonIdx + 1).trim().take(8)
        if (dataHex.length >= 4) "$companyId:$dataHex" else null
    }
}

fun extractPrefixesFromFingerprintMap(map: Map<Int, String>?): List<String> {
    if (map.isNullOrEmpty()) return emptyList()
    return map.entries.mapNotNull { (id, hex) ->
        val idHex = id.toString(16).padStart(4, '0')
        val prefix = hex.take(8)
        if (prefix.length >= 4) "$idHex:$prefix" else null
    }
}
```

Commit this before Task 17 deletes `MatchLearnedSignatureUseCase.kt`.
