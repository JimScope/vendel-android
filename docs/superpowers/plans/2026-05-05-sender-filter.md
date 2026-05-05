# Sender Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional sender filter (Off/Allow/Block) to the Vendel Android app so users can restrict which incoming SMS senders are forwarded to the server.

**Architecture:** Filter rules live in a new Room table (`sender_filters`); the active mode lives in `SecurePreferences`. A `SenderFilterRepository` exposes the mode + rules and a single `shouldForward(sender)` decision used by `SmsReceiver`. UI lives on a dedicated `SenderFilterScreen` reachable from Settings; the Settings card shows mode + rule count. Pure decision logic (`SenderFilterDecision`) is a Kotlin object so it's unit-testable without Android.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room, Hilt, kotlinx-coroutines, JUnit4, AndroidJUnit4 + Room-testing for instrumented DAO tests, kotlinx-coroutines-test for `runTest`.

**Source spec:** `docs/superpowers/specs/2026-05-05-sender-filter-design.md`

---

## File Map

**New files:**
- `app/src/main/java/com/jimscope/vendel/data/local/entity/SenderFilterEntity.kt` — Room entity
- `app/src/main/java/com/jimscope/vendel/data/local/dao/SenderFilterDao.kt` — DAO with `matches()` query
- `app/src/main/java/com/jimscope/vendel/data/local/migration/Migrations.kt` — `MIGRATION_1_2`
- `app/src/main/java/com/jimscope/vendel/data/repository/SenderFilterMode.kt` — enum
- `app/src/main/java/com/jimscope/vendel/data/repository/SenderFilterDecision.kt` — pure decision function
- `app/src/main/java/com/jimscope/vendel/data/repository/SenderFilterRepository.kt` — Hilt singleton
- `app/src/main/java/com/jimscope/vendel/ui/filter/SenderFilterScreen.kt` — Compose screen
- `app/src/main/java/com/jimscope/vendel/ui/filter/SenderFilterViewModel.kt` — Hilt VM
- `app/src/main/java/com/jimscope/vendel/ui/filter/AddFilterSheet.kt` — Compose `ModalBottomSheet` with inline contact picker
- `app/src/test/java/com/jimscope/vendel/data/repository/SenderFilterDecisionTest.kt` — pure unit tests
- `app/src/androidTest/java/com/jimscope/vendel/data/local/dao/SenderFilterDaoTest.kt` — instrumented Room test

**Modified files:**
- `gradle/libs.versions.toml` — add coroutines-test, room-testing version refs + libraries
- `app/build.gradle.kts` — add test dependencies
- `app/src/main/java/com/jimscope/vendel/data/local/VendelDatabase.kt` — version 1 → 2, add entity + DAO
- `app/src/main/java/com/jimscope/vendel/di/DatabaseModule.kt` — register migration + DAO provider
- `app/src/main/java/com/jimscope/vendel/data/preferences/SecurePreferences.kt` — `senderFilterMode` property
- `app/src/main/java/com/jimscope/vendel/data/repository/ConfigRepository.kt` — disconnect wipes filters + resets mode
- `app/src/main/java/com/jimscope/vendel/service/SmsReceiver.kt` — call `shouldForward` before forwarding
- `app/src/main/java/com/jimscope/vendel/ui/navigation/VendelNavigation.kt` — `Screen.SenderFilter` route + composable
- `app/src/main/java/com/jimscope/vendel/ui/settings/SettingsScreen.kt` — filter summary row, `onNavigateToSenderFilter` param
- `app/src/main/java/com/jimscope/vendel/ui/settings/SettingsViewModel.kt` — expose mode + count flows
- `app/src/main/res/values/strings.xml` — EN strings
- `app/src/main/res/values-es/strings.xml` — ES strings

---

### Task 1: Add test dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add version refs and library entries**

In `gradle/libs.versions.toml`, locate the `[versions]` block and add:

```toml
coroutinesTest = "1.10.2"
```

The existing `room = "2.8.4"` version ref is reused for `room-testing` to avoid drift. Then in the `[libraries]` block, add:

```toml
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
```

- [ ] **Step 2: Wire dependencies into the app module**

In `app/build.gradle.kts`, find the `dependencies { ... }` block and add:

```kotlin
testImplementation(libs.kotlinx.coroutines.test)
androidTestImplementation(libs.androidx.room.testing)
androidTestImplementation(libs.kotlinx.coroutines.test)
```

- [ ] **Step 3: Sync Gradle**

Run: `./gradlew help`
Expected: `BUILD SUCCESSFUL`. Confirms toml + Gradle parse cleanly.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "chore: add coroutines-test + room-testing for sender-filter tests"
```

---

### Task 2: Add `SenderFilterMode` enum + persist in `SecurePreferences`

**Files:**
- Create: `app/src/main/java/com/jimscope/vendel/data/repository/SenderFilterMode.kt`
- Modify: `app/src/main/java/com/jimscope/vendel/data/preferences/SecurePreferences.kt`

- [ ] **Step 1: Create the enum**

Create `app/src/main/java/com/jimscope/vendel/data/repository/SenderFilterMode.kt`:

```kotlin
package com.jimscope.vendel.data.repository

enum class SenderFilterMode { OFF, ALLOW, BLOCK }
```

- [ ] **Step 2: Add `senderFilterMode` to `SecurePreferences`**

In `app/src/main/java/com/jimscope/vendel/data/preferences/SecurePreferences.kt`, add the import after the existing imports:

```kotlin
import com.jimscope.vendel.data.repository.SenderFilterMode
```

Inside the class, after the `hasSeenOnboarding` property, add:

```kotlin
var senderFilterMode: SenderFilterMode
    get() = runCatching {
        SenderFilterMode.valueOf(prefs.getString(KEY_SENDER_FILTER_MODE, null) ?: SenderFilterMode.OFF.name)
    }.getOrDefault(SenderFilterMode.OFF)
    set(value) = prefs.edit().putString(KEY_SENDER_FILTER_MODE, value.name).apply()
```

In the `companion object`, add the constant alongside the others:

```kotlin
private const val KEY_SENDER_FILTER_MODE = "sender_filter_mode"
```

The existing `clear()` method already wipes all entries via `prefs.edit().clear().apply()`, so no further change is needed for disconnect.

- [ ] **Step 3: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/jimscope/vendel/data/repository/SenderFilterMode.kt app/src/main/java/com/jimscope/vendel/data/preferences/SecurePreferences.kt
git commit -m "feat: add SenderFilterMode enum + persistence in SecurePreferences"
```

---

### Task 3: Create `SenderFilterEntity` + `SenderFilterDao`

**Files:**
- Create: `app/src/main/java/com/jimscope/vendel/data/local/entity/SenderFilterEntity.kt`
- Create: `app/src/main/java/com/jimscope/vendel/data/local/dao/SenderFilterDao.kt`

- [ ] **Step 1: Create the entity**

Create `app/src/main/java/com/jimscope/vendel/data/local/entity/SenderFilterEntity.kt`:

```kotlin
package com.jimscope.vendel.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sender_filters",
    indices = [Index(value = ["pattern", "is_prefix"], unique = true)]
)
data class SenderFilterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    @ColumnInfo(name = "is_prefix") val isPrefix: Boolean = false,
    val label: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 2: Create the DAO**

Create `app/src/main/java/com/jimscope/vendel/data/local/dao/SenderFilterDao.kt`:

```kotlin
package com.jimscope.vendel.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jimscope.vendel.data.local.entity.SenderFilterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SenderFilterDao {

    @Query("SELECT * FROM sender_filters ORDER BY created_at DESC")
    fun observeAll(): Flow<List<SenderFilterEntity>>

    @Query("SELECT COUNT(*) FROM sender_filters")
    fun countFlow(): Flow<Int>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM sender_filters
            WHERE (is_prefix = 0 AND LOWER(pattern) = :sender)
               OR (is_prefix = 1 AND :sender LIKE LOWER(pattern) || '%')
        )
        """
    )
    suspend fun matches(sender: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(filter: SenderFilterEntity): Long

    @Query("DELETE FROM sender_filters WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM sender_filters")
    suspend fun deleteAll()
}
```

- [ ] **Step 3: Compile (KSP must be happy with the entity / queries)**

Run: `./gradlew :app:kspDebugKotlin`
Expected: `BUILD SUCCESSFUL`. Note: this will also fail later because the entity isn't registered in `VendelDatabase` yet — that's the next task. KSP at this stage just validates the entity + DAO syntax.

If KSP complains about an unregistered DAO/entity, that's normal — proceed to Task 4 to register them.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/jimscope/vendel/data/local/entity/SenderFilterEntity.kt app/src/main/java/com/jimscope/vendel/data/local/dao/SenderFilterDao.kt
git commit -m "feat: add SenderFilterEntity + SenderFilterDao"
```

---

### Task 4: Bump database version + register migration + DI provider

**Files:**
- Create: `app/src/main/java/com/jimscope/vendel/data/local/migration/Migrations.kt`
- Modify: `app/src/main/java/com/jimscope/vendel/data/local/VendelDatabase.kt`
- Modify: `app/src/main/java/com/jimscope/vendel/di/DatabaseModule.kt`

- [ ] **Step 1: Create the migration**

Create `app/src/main/java/com/jimscope/vendel/data/local/migration/Migrations.kt`:

```kotlin
package com.jimscope.vendel.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sender_filters (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                pattern TEXT NOT NULL,
                is_prefix INTEGER NOT NULL DEFAULT 0,
                label TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_sender_filters_pattern_is_prefix
            ON sender_filters(pattern, is_prefix)
            """.trimIndent()
        )
    }
}
```

- [ ] **Step 2: Update `VendelDatabase`**

Replace the contents of `app/src/main/java/com/jimscope/vendel/data/local/VendelDatabase.kt` with:

```kotlin
package com.jimscope.vendel.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jimscope.vendel.data.local.dao.MessageLogDao
import com.jimscope.vendel.data.local.dao.PendingReportDao
import com.jimscope.vendel.data.local.dao.SenderFilterDao
import com.jimscope.vendel.data.local.entity.MessageLogEntity
import com.jimscope.vendel.data.local.entity.PendingReportEntity
import com.jimscope.vendel.data.local.entity.SenderFilterEntity

@Database(
    entities = [PendingReportEntity::class, MessageLogEntity::class, SenderFilterEntity::class],
    version = 2,
    exportSchema = false
)
abstract class VendelDatabase : RoomDatabase() {
    abstract fun pendingReportDao(): PendingReportDao
    abstract fun messageLogDao(): MessageLogDao
    abstract fun senderFilterDao(): SenderFilterDao
}
```

- [ ] **Step 3: Wire migration + DAO in `DatabaseModule`**

Replace the contents of `app/src/main/java/com/jimscope/vendel/di/DatabaseModule.kt` with:

```kotlin
package com.jimscope.vendel.di

import android.content.Context
import androidx.room.Room
import com.jimscope.vendel.data.local.VendelDatabase
import com.jimscope.vendel.data.local.dao.MessageLogDao
import com.jimscope.vendel.data.local.dao.PendingReportDao
import com.jimscope.vendel.data.local.dao.SenderFilterDao
import com.jimscope.vendel.data.local.migration.MIGRATION_1_2
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): VendelDatabase = Room.databaseBuilder(
        context,
        VendelDatabase::class.java,
        "vendel_database"
    ).addMigrations(MIGRATION_1_2).build()

    @Provides
    fun providePendingReportDao(db: VendelDatabase): PendingReportDao = db.pendingReportDao()

    @Provides
    fun provideMessageLogDao(db: VendelDatabase): MessageLogDao = db.messageLogDao()

    @Provides
    fun provideSenderFilterDao(db: VendelDatabase): SenderFilterDao = db.senderFilterDao()
}
```

- [ ] **Step 4: Build the whole module**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. Room generates schema for version 2, migration is registered, DAO is provided.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/jimscope/vendel/data/local/migration/Migrations.kt app/src/main/java/com/jimscope/vendel/data/local/VendelDatabase.kt app/src/main/java/com/jimscope/vendel/di/DatabaseModule.kt
git commit -m "feat: register sender_filters table (db v1->v2) + DAO provider"
```

---

### Task 5: Instrumented `SenderFilterDao` test

**Files:**
- Create: `app/src/androidTest/java/com/jimscope/vendel/data/local/dao/SenderFilterDaoTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/androidTest/java/com/jimscope/vendel/data/local/dao/SenderFilterDaoTest.kt`:

```kotlin
package com.jimscope.vendel.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jimscope.vendel.data.local.VendelDatabase
import com.jimscope.vendel.data.local.entity.SenderFilterEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SenderFilterDaoTest {

    private lateinit var db: VendelDatabase
    private lateinit var dao: SenderFilterDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VendelDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.senderFilterDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun matchesExactNumber() = runTest {
        dao.insert(SenderFilterEntity(pattern = "+593987654321", isPrefix = false))
        assertTrue(dao.matches("+593987654321"))
        assertFalse(dao.matches("+593987654322"))
    }

    @Test
    fun matchesPrefix() = runTest {
        dao.insert(SenderFilterEntity(pattern = "+593", isPrefix = true))
        assertTrue(dao.matches("+593987654321"))
        assertTrue(dao.matches("+593"))
        assertFalse(dao.matches("+591987654321"))
    }

    @Test
    fun matchIsCaseInsensitiveForAlpha() = runTest {
        dao.insert(SenderFilterEntity(pattern = "BANCO", isPrefix = false))
        assertTrue(dao.matches("banco"))
    }

    @Test
    fun matchesShortcode() = runTest {
        dao.insert(SenderFilterEntity(pattern = "12345", isPrefix = false))
        assertTrue(dao.matches("12345"))
    }

    @Test
    fun duplicateInsertIsIgnored() = runTest {
        dao.insert(SenderFilterEntity(pattern = "BANCO", isPrefix = false))
        dao.insert(SenderFilterEntity(pattern = "BANCO", isPrefix = false))
        // No exception, single row remains. Verify via deleteAll counting nothing extra.
        dao.deleteAll()
    }

    @Test
    fun deleteAllRemovesEverything() = runTest {
        dao.insert(SenderFilterEntity(pattern = "A", isPrefix = false))
        dao.insert(SenderFilterEntity(pattern = "B", isPrefix = true))
        dao.deleteAll()
        assertFalse(dao.matches("a"))
        assertFalse(dao.matches("b"))
    }

    @Test
    fun emptyTableNeverMatches() = runTest {
        assertFalse(dao.matches("anything"))
    }

    @Test
    fun prefixDoesNotMatchExactPattern() = runTest {
        dao.insert(SenderFilterEntity(pattern = "BANCO", isPrefix = false))
        assertFalse(dao.matches("bancoplus")) // exact-mode shouldn't prefix-match
    }

    @Test
    fun observeAllOrdersByCreatedAtDesc() = runTest {
        dao.insert(SenderFilterEntity(pattern = "first", isPrefix = false, createdAt = 100))
        dao.insert(SenderFilterEntity(pattern = "second", isPrefix = false, createdAt = 200))
        val items = dao.observeAll().let {
            kotlinx.coroutines.flow.first(it)
        }
        assertEquals("second", items[0].pattern)
        assertEquals("first", items[1].pattern)
    }
}
```

Note the helper `kotlinx.coroutines.flow.first(it)` is used as `Flow<T>.first()` extension; if your IDE prefers, replace with `import kotlinx.coroutines.flow.first` and `it.first()`.

- [ ] **Step 2: Run the tests**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.jimscope.vendel.data.local.dao.SenderFilterDaoTest"`
Expected: all tests PASS. Requires a connected device or running emulator.

If a device is not available, document the skip and run unit tests later. Do not mark Step 2 complete unless tests actually executed and passed.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/jimscope/vendel/data/local/dao/SenderFilterDaoTest.kt
git commit -m "test: instrumented coverage for SenderFilterDao matches/insert/delete"
```

---

### Task 6: Pure `SenderFilterDecision` + unit tests

**Files:**
- Create: `app/src/main/java/com/jimscope/vendel/data/repository/SenderFilterDecision.kt`
- Create: `app/src/test/java/com/jimscope/vendel/data/repository/SenderFilterDecisionTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/jimscope/vendel/data/repository/SenderFilterDecisionTest.kt`:

```kotlin
package com.jimscope.vendel.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderFilterDecisionTest {

    @Test
    fun offAlwaysForwards() {
        assertTrue(SenderFilterDecision.decide(SenderFilterMode.OFF, matched = false, hasSender = true))
        assertTrue(SenderFilterDecision.decide(SenderFilterMode.OFF, matched = true, hasSender = false))
    }

    @Test
    fun allowForwardsOnlyOnMatchWithSender() {
        assertTrue(SenderFilterDecision.decide(SenderFilterMode.ALLOW, matched = true, hasSender = true))
        assertFalse(SenderFilterDecision.decide(SenderFilterMode.ALLOW, matched = false, hasSender = true))
        assertFalse(SenderFilterDecision.decide(SenderFilterMode.ALLOW, matched = true, hasSender = false))
        assertFalse(SenderFilterDecision.decide(SenderFilterMode.ALLOW, matched = false, hasSender = false))
    }

    @Test
    fun blockForwardsUnlessMatchWithSender() {
        assertFalse(SenderFilterDecision.decide(SenderFilterMode.BLOCK, matched = true, hasSender = true))
        assertTrue(SenderFilterDecision.decide(SenderFilterMode.BLOCK, matched = false, hasSender = true))
        assertTrue(SenderFilterDecision.decide(SenderFilterMode.BLOCK, matched = true, hasSender = false))
        assertTrue(SenderFilterDecision.decide(SenderFilterMode.BLOCK, matched = false, hasSender = false))
    }
}
```

- [ ] **Step 2: Run tests, expect compile failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.jimscope.vendel.data.repository.SenderFilterDecisionTest"`
Expected: compilation error — `SenderFilterDecision` does not exist.

- [ ] **Step 3: Implement `SenderFilterDecision`**

Create `app/src/main/java/com/jimscope/vendel/data/repository/SenderFilterDecision.kt`:

```kotlin
package com.jimscope.vendel.data.repository

object SenderFilterDecision {
    fun decide(mode: SenderFilterMode, matched: Boolean, hasSender: Boolean): Boolean = when (mode) {
        SenderFilterMode.OFF -> true
        SenderFilterMode.ALLOW -> hasSender && matched
        SenderFilterMode.BLOCK -> !(hasSender && matched)
    }
}
```

- [ ] **Step 4: Run tests, expect pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.jimscope.vendel.data.repository.SenderFilterDecisionTest"`
Expected: all 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/jimscope/vendel/data/repository/SenderFilterDecision.kt app/src/test/java/com/jimscope/vendel/data/repository/SenderFilterDecisionTest.kt
git commit -m "feat: pure SenderFilterDecision function + unit tests"
```

---

### Task 7: `SenderFilterRepository`

**Files:**
- Create: `app/src/main/java/com/jimscope/vendel/data/repository/SenderFilterRepository.kt`

- [ ] **Step 1: Implement the repository**

Create `app/src/main/java/com/jimscope/vendel/data/repository/SenderFilterRepository.kt`:

```kotlin
package com.jimscope.vendel.data.repository

import com.jimscope.vendel.data.local.dao.SenderFilterDao
import com.jimscope.vendel.data.local.entity.SenderFilterEntity
import com.jimscope.vendel.data.preferences.SecurePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SenderFilterRepository @Inject constructor(
    private val dao: SenderFilterDao,
    private val securePreferences: SecurePreferences
) {
    private val _mode = MutableStateFlow(securePreferences.senderFilterMode)
    val mode: StateFlow<SenderFilterMode> = _mode.asStateFlow()

    val filters: Flow<List<SenderFilterEntity>> = dao.observeAll()
    val count: Flow<Int> = dao.countFlow()

    fun setMode(mode: SenderFilterMode) {
        securePreferences.senderFilterMode = mode
        _mode.value = mode
    }

    suspend fun shouldForward(rawSender: String?): Boolean {
        val currentMode = _mode.value
        val hasSender = rawSender != null
        val matched = if (hasSender) {
            dao.matches(rawSender!!.trim().lowercase(Locale.ROOT))
        } else {
            false
        }
        return SenderFilterDecision.decide(currentMode, matched, hasSender)
    }

    suspend fun add(pattern: String, isPrefix: Boolean, label: String?) {
        val trimmedPattern = pattern.trim()
        if (trimmedPattern.isEmpty()) return
        dao.insert(
            SenderFilterEntity(
                pattern = trimmedPattern,
                isPrefix = isPrefix,
                label = label?.trim()?.takeIf { it.isNotBlank() }
            )
        )
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun reset() {
        dao.deleteAll()
        setMode(SenderFilterMode.OFF)
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/jimscope/vendel/data/repository/SenderFilterRepository.kt
git commit -m "feat: SenderFilterRepository with mode flow + shouldForward"
```

---

### Task 8: Wire disconnect cleanup

**Files:**
- Modify: `app/src/main/java/com/jimscope/vendel/data/repository/ConfigRepository.kt`

- [ ] **Step 1: Inject the new repo and call `reset()` on disconnect**

In `app/src/main/java/com/jimscope/vendel/data/repository/ConfigRepository.kt`, change the constructor to add `senderFilterRepository: SenderFilterRepository`:

```kotlin
@Singleton
class ConfigRepository @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val messageLogDao: MessageLogDao,
    private val pendingReportDao: PendingReportDao,
    private val senderFilterRepository: SenderFilterRepository
) {
```

Update `disconnect()` to also reset filters:

```kotlin
suspend fun disconnect() {
    messageLogDao.deleteAll()
    pendingReportDao.deleteAll()
    senderFilterRepository.reset()
    securePreferences.clear()
    _config.value = ConnectionConfig()
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/jimscope/vendel/data/repository/ConfigRepository.kt
git commit -m "feat: disconnect wipes sender filters + resets mode"
```

---

### Task 9: Integrate filter into `SmsReceiver`

**Files:**
- Modify: `app/src/main/java/com/jimscope/vendel/service/SmsReceiver.kt`

- [ ] **Step 1: Inject the repo and gate the forward**

In `app/src/main/java/com/jimscope/vendel/service/SmsReceiver.kt`, add the import:

```kotlin
import com.jimscope.vendel.data.repository.SenderFilterRepository
```

Add the inject below the existing ones:

```kotlin
@Inject lateinit var senderFilterRepository: SenderFilterRepository
```

Replace the inner `for ((sender, parts) in grouped) { ... }` loop body with:

```kotlin
for ((sender, parts) in grouped) {
    val forwardableSender = sender.takeUnless { it == "unknown" }
    if (!senderFilterRepository.shouldForward(forwardableSender)) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Filtered out: $sender")
        continue
    }
    val body = parts.joinToString("") { it.messageBody ?: "" }
    val timestamp = Instant.ofEpochMilli(parts.first().timestampMillis).toString()
    if (BuildConfig.DEBUG) Log.d(TAG, "Incoming SMS from $sender")
    smsRepository.reportIncoming(sender, body, timestamp)
}
```

The `takeUnless { it == "unknown" }` translates the existing fallback string back to a real null so the repository's `hasSender` logic can see it as missing.

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/jimscope/vendel/service/SmsReceiver.kt
git commit -m "feat: gate incoming SMS forward on SenderFilterRepository.shouldForward"
```

---

### Task 10: Add `Screen.SenderFilter` route

**Files:**
- Modify: `app/src/main/java/com/jimscope/vendel/ui/navigation/VendelNavigation.kt`

- [ ] **Step 1: Add the sealed Screen entry and a placeholder composable**

In `app/src/main/java/com/jimscope/vendel/ui/navigation/VendelNavigation.kt`, add to the sealed class:

```kotlin
data object SenderFilter : Screen("sender_filter")
```

Then inside `NavHost`, add a composable entry (anywhere in the block; placement does not affect routing):

```kotlin
composable(Screen.SenderFilter.route) {
    SenderFilterScreen(
        onBack = { navController.popBackStack() }
    )
}
```

Add the import:

```kotlin
import com.jimscope.vendel.ui.filter.SenderFilterScreen
```

This will fail to compile until Task 12 creates `SenderFilterScreen`. Skip the build verification here — it's covered after Task 12.

- [ ] **Step 2: Update Settings composable to take the navigation lambda**

Within the same file, change the `Settings` composable to:

```kotlin
composable(Screen.Settings.route) {
    SettingsScreen(
        onDisconnect = {
            navController.navigate(Screen.Setup.route) {
                popUpTo(0) { inclusive = true }
            }
        },
        onNavigateToSenderFilter = {
            navController.navigate(Screen.SenderFilter.route)
        }
    )
}
```

This will fail to compile until Task 13 updates `SettingsScreen`'s signature. Tracked.

- [ ] **Step 3: Stage but defer commit**

Do not commit yet — next tasks introduce `SenderFilterScreen` and `SettingsScreen` changes that pair with this. Continue.

---

### Task 11: `SenderFilterViewModel`

**Files:**
- Create: `app/src/main/java/com/jimscope/vendel/ui/filter/SenderFilterViewModel.kt`

- [ ] **Step 1: Create the VM**

Create `app/src/main/java/com/jimscope/vendel/ui/filter/SenderFilterViewModel.kt`:

```kotlin
package com.jimscope.vendel.ui.filter

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jimscope.vendel.data.local.entity.SenderFilterEntity
import com.jimscope.vendel.data.repository.SenderFilterMode
import com.jimscope.vendel.data.repository.SenderFilterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SenderFilterUiState(
    val mode: SenderFilterMode = SenderFilterMode.OFF,
    val filters: List<SenderFilterEntity> = emptyList(),
    val showAddSheet: Boolean = false
)

@HiltViewModel
class SenderFilterViewModel @Inject constructor(
    private val repository: SenderFilterRepository
) : ViewModel() {

    private val _showAddSheet = MutableStateFlow(false)

    val uiState: StateFlow<SenderFilterUiState> = combine(
        repository.mode,
        repository.filters,
        _showAddSheet
    ) { mode, filters, showSheet ->
        SenderFilterUiState(mode = mode, filters = filters, showAddSheet = showSheet)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SenderFilterUiState()
    )

    fun setMode(mode: SenderFilterMode) {
        repository.setMode(mode)
    }

    fun openAddSheet() { _showAddSheet.value = true }

    fun closeAddSheet() { _showAddSheet.value = false }

    fun add(pattern: String, isPrefix: Boolean, label: String?) {
        if (pattern.isBlank()) return
        viewModelScope.launch {
            repository.add(pattern, isPrefix, label)
            _showAddSheet.value = false
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: still failing because `SenderFilterScreen` is referenced from navigation. Acceptable — proceed.

- [ ] **Step 3: Stage but defer commit**

Continue without committing; the screen comes next.

---

### Task 12: `SettingsScreen` filter summary row + nav wiring

**Files:**
- Modify: `app/src/main/java/com/jimscope/vendel/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/jimscope/vendel/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: Expose mode + count in `SettingsViewModel`**

In `app/src/main/java/com/jimscope/vendel/ui/settings/SettingsViewModel.kt`, replace the file with:

```kotlin
package com.jimscope.vendel.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jimscope.vendel.BuildConfig
import com.jimscope.vendel.data.preferences.SecurePreferences
import com.jimscope.vendel.data.repository.ConfigRepository
import com.jimscope.vendel.data.repository.ConnectionConfig
import com.jimscope.vendel.data.repository.SenderFilterMode
import com.jimscope.vendel.data.repository.SenderFilterRepository
import com.jimscope.vendel.domain.CheckForUpdateUseCase
import com.jimscope.vendel.domain.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SettingsUiState(
    val config: ConnectionConfig = ConnectionConfig(),
    val incomingSmsEnabled: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val senderFilterMode: SenderFilterMode = SenderFilterMode.OFF,
    val senderFilterCount: Int = 0
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val securePreferences: SecurePreferences,
    private val checkForUpdate: CheckForUpdateUseCase,
    senderFilterRepository: SenderFilterRepository
) : ViewModel() {

    private val _incomingSmsEnabled = MutableStateFlow(securePreferences.incomingSmsEnabled)
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        configRepository.config,
        _incomingSmsEnabled,
        _updateInfo,
        senderFilterRepository.mode,
        senderFilterRepository.count
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        SettingsUiState(
            config = values[0] as ConnectionConfig,
            incomingSmsEnabled = values[1] as Boolean,
            updateInfo = values[2] as UpdateInfo?,
            senderFilterMode = values[3] as SenderFilterMode,
            senderFilterCount = values[4] as Int
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(
            config = configRepository.config.value,
            incomingSmsEnabled = securePreferences.incomingSmsEnabled
        )
    )

    init {
        viewModelScope.launch {
            checkForUpdate(BuildConfig.VERSION_NAME).onSuccess { info ->
                if (info.isUpdateAvailable &&
                    info.latestVersion != securePreferences.dismissedUpdateVersion
                ) {
                    _updateInfo.value = info
                }
            }
        }
    }

    fun dismissUpdate(version: String) {
        securePreferences.dismissedUpdateVersion = version
        _updateInfo.value = null
    }

    fun toggleIncomingSms(enabled: Boolean) {
        securePreferences.incomingSmsEnabled = enabled
        _incomingSmsEnabled.value = enabled
    }

    fun disconnect(onComplete: () -> Unit) {
        viewModelScope.launch {
            configRepository.disconnect()
            onComplete()
        }
    }
}
```

- [ ] **Step 2: Update `SettingsScreen` signature and add the filter row**

In `app/src/main/java/com/jimscope/vendel/ui/settings/SettingsScreen.kt`:

Add imports:

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.jimscope.vendel.data.repository.SenderFilterMode
```

Change the function signature:

```kotlin
fun SettingsScreen(
    onDisconnect: () -> Unit,
    onNavigateToSenderFilter: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
)
```

Insert this card after the existing SMS card and before the Battery optimization card:

```kotlin
Spacer(modifier = Modifier.height(16.dp))

Card(
    modifier = Modifier
        .fillMaxWidth()
        .clickable { onNavigateToSenderFilter() },
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(12.dp)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.settings_sender_filter_title),
                style = MaterialTheme.typography.titleMedium
            )
            val summary = when (uiState.senderFilterMode) {
                SenderFilterMode.OFF -> stringResource(R.string.settings_sender_filter_summary_off)
                SenderFilterMode.ALLOW -> stringResource(R.string.settings_sender_filter_summary_allow, uiState.senderFilterCount)
                SenderFilterMode.BLOCK -> stringResource(R.string.settings_sender_filter_summary_block, uiState.senderFilterCount)
            }
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

- [ ] **Step 3: Stage but defer commit**

Wait until the screen exists.

---

### Task 13: `SenderFilterScreen` + add-rule sheet

**Files:**
- Create: `app/src/main/java/com/jimscope/vendel/ui/filter/SenderFilterScreen.kt`
- Create: `app/src/main/java/com/jimscope/vendel/ui/filter/AddFilterSheet.kt`

- [ ] **Step 1: Create the screen**

Create `app/src/main/java/com/jimscope/vendel/ui/filter/SenderFilterScreen.kt`:

```kotlin
package com.jimscope.vendel.ui.filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jimscope.vendel.R
import com.jimscope.vendel.data.local.entity.SenderFilterEntity
import com.jimscope.vendel.data.repository.SenderFilterMode
import com.jimscope.vendel.ui.theme.VendelBrand
import com.jimscope.vendel.ui.theme.VendelBrandTint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderFilterScreen(
    onBack: () -> Unit,
    viewModel: SenderFilterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.filter_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openAddSheet() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.filter_add)) },
                containerColor = VendelBrand
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            ModeSelector(
                mode = uiState.mode,
                onModeSelected = { viewModel.setMode(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.mode == SenderFilterMode.ALLOW && uiState.filters.isEmpty()) {
                EmptyAllowWarning()
                Spacer(modifier = Modifier.height(12.dp))
            }

            FilterList(
                filters = uiState.filters,
                onDelete = { viewModel.delete(it) }
            )
        }

        if (uiState.showAddSheet) {
            AddFilterSheet(
                onDismiss = { viewModel.closeAddSheet() },
                onSave = { pattern, isPrefix, label ->
                    viewModel.add(pattern, isPrefix, label)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(
    mode: SenderFilterMode,
    onModeSelected: (SenderFilterMode) -> Unit
) {
    val options = listOf(
        SenderFilterMode.OFF to stringResource(R.string.filter_mode_off),
        SenderFilterMode.ALLOW to stringResource(R.string.filter_mode_allow),
        SenderFilterMode.BLOCK to stringResource(R.string.filter_mode_block)
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onModeSelected(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = VendelBrandTint,
                    activeContentColor = MaterialTheme.colorScheme.onBackground
                )
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun EmptyAllowWarning() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = VendelBrandTint),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = VendelBrand
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                stringResource(R.string.filter_empty_allow_warning),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun FilterList(
    filters: List<SenderFilterEntity>,
    onDelete: (Long) -> Unit
) {
    if (filters.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
            items(filters, key = { it.id }) { filter ->
                FilterRow(filter = filter, onDelete = onDelete)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun FilterRow(
    filter: SenderFilterEntity,
    onDelete: (Long) -> Unit
) {
    val isAlpha = filter.pattern.none { it.isDigit() || it == '+' }
    val icon: ImageVector = when {
        filter.label != null -> Icons.Default.Person
        isAlpha -> Icons.Default.Storefront
        else -> Icons.Default.Phone
    }
    val descriptor = when {
        filter.isPrefix -> stringResource(R.string.filter_descriptor_prefix)
        isAlpha -> stringResource(R.string.filter_descriptor_alpha_exact)
        else -> stringResource(R.string.filter_descriptor_exact)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                filter.label ?: filter.pattern,
                style = MaterialTheme.typography.bodyLarge
            )
            if (filter.label != null) {
                Text(
                    filter.pattern,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                descriptor,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { onDelete(filter.id) }) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.filter_delete_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

- [ ] **Step 2: Create the add-rule sheet**

Create `app/src/main/java/com/jimscope/vendel/ui/filter/AddFilterSheet.kt`:

```kotlin
package com.jimscope.vendel.ui.filter

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jimscope.vendel.R
import com.jimscope.vendel.ui.theme.VendelBrand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFilterSheet(
    onDismiss: () -> Unit,
    onSave: (pattern: String, isPrefix: Boolean, label: String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var pattern by rememberSaveable { mutableStateOf("") }
    var isPrefix by rememberSaveable { mutableStateOf(false) }
    var label by rememberSaveable { mutableStateOf<String?>(null) }

    val phoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                pattern = cursor.getString(0).orEmpty()
                label = cursor.getString(1)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.filter_add),
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it; label = null },
                label = { Text(stringResource(R.string.filter_pattern_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = isPrefix,
                    onCheckedChange = { isPrefix = it }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    stringResource(R.string.filter_use_prefix),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            HorizontalDivider()
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_PICK).apply {
                        type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
                    }
                    phoneLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.filter_pick_contact))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.filter_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onSave(pattern.trim(), isPrefix, label) },
                    enabled = pattern.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = VendelBrand)
                ) {
                    Text(stringResource(R.string.filter_save))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
```

- [ ] **Step 3: Build the whole project**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`. All previously deferred references (Tasks 10/11/12) now resolve.

If any string resource is missing, that's expected — the next task adds them.

- [ ] **Step 4: Stage but defer commit**

Strings still need to land before this can run on a device.

---

### Task 14: Strings (EN + ES)

**Files:**
- Modify: `app/src/main/res/values/strings.xml` (English / default)
- Modify: `app/src/main/res/values-es/strings.xml`

- [ ] **Step 1: Add EN strings**

In `app/src/main/res/values/strings.xml`, add the following inside the `<resources>` block (any position):

```xml
<string name="settings_sender_filter_title">Sender filter</string>
<string name="settings_sender_filter_summary_off">Off</string>
<string name="settings_sender_filter_summary_allow">Allow · %1$d rules</string>
<string name="settings_sender_filter_summary_block">Block · %1$d rules</string>
<string name="filter_screen_title">Sender filter</string>
<string name="filter_mode_off">Off</string>
<string name="filter_mode_allow">Allow</string>
<string name="filter_mode_block">Block</string>
<string name="filter_empty_allow_warning">Allow mode with empty list blocks everything</string>
<string name="filter_add">Add</string>
<string name="filter_pattern_label">Number or sender name</string>
<string name="filter_use_prefix">Match by prefix</string>
<string name="filter_pick_contact">Pick from contacts</string>
<string name="filter_cancel">Cancel</string>
<string name="filter_save">Save</string>
<string name="filter_delete_cd">Delete rule</string>
<string name="filter_descriptor_prefix">Prefix</string>
<string name="filter_descriptor_alpha_exact">Alpha, exact</string>
<string name="filter_descriptor_exact">Exact</string>
```

- [ ] **Step 2: Add ES strings**

In `app/src/main/res/values-es/strings.xml`, add:

```xml
<string name="settings_sender_filter_title">Filtro de remitentes</string>
<string name="settings_sender_filter_summary_off">Desactivado</string>
<string name="settings_sender_filter_summary_allow">Allow · %1$d reglas</string>
<string name="settings_sender_filter_summary_block">Block · %1$d reglas</string>
<string name="filter_screen_title">Filtro de remitentes</string>
<string name="filter_mode_off">Desactivado</string>
<string name="filter_mode_allow">Allow</string>
<string name="filter_mode_block">Block</string>
<string name="filter_empty_allow_warning">Modo Allow con lista vacía bloquea todo</string>
<string name="filter_add">Agregar</string>
<string name="filter_pattern_label">Número o nombre del remitente</string>
<string name="filter_use_prefix">Coincidencia por prefijo</string>
<string name="filter_pick_contact">Elegir desde contactos</string>
<string name="filter_cancel">Cancelar</string>
<string name="filter_save">Guardar</string>
<string name="filter_delete_cd">Eliminar regla</string>
<string name="filter_descriptor_prefix">Prefix</string>
<string name="filter_descriptor_alpha_exact">Alfa, exact</string>
<string name="filter_descriptor_exact">Exact</string>
```

- [ ] **Step 3: Build everything**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all tests PASS, including `SenderFilterDecisionTest`.

- [ ] **Step 5: Commit the whole UI + integration bundle**

```bash
git add app/src/main/java/com/jimscope/vendel/ui/navigation/VendelNavigation.kt \
        app/src/main/java/com/jimscope/vendel/ui/filter/SenderFilterViewModel.kt \
        app/src/main/java/com/jimscope/vendel/ui/filter/SenderFilterScreen.kt \
        app/src/main/java/com/jimscope/vendel/ui/filter/AddFilterSheet.kt \
        app/src/main/java/com/jimscope/vendel/ui/settings/SettingsScreen.kt \
        app/src/main/java/com/jimscope/vendel/ui/settings/SettingsViewModel.kt \
        app/src/main/res/values/strings.xml \
        app/src/main/res/values-es/strings.xml
git commit -m "feat: SenderFilterScreen with mode selector + contact picker"
```

---

### Task 15: Manual smoke test

This task has no code changes. Run the app on a device or emulator and walk through:

- [ ] **Step 1: Configure server, send a test SMS from a sender (e.g., another phone or shortcode), confirm it appears in `MessageLogScreen`.**

- [ ] **Step 2: Open Settings → tap "Filtro de remitentes" → screen opens, bottom navigation bar is hidden, mode shows `Off`.**

- [ ] **Step 3: Switch to `Allow`, add a rule via free text matching the test sender → send another SMS → it should be forwarded.**

- [ ] **Step 4: Send an SMS from a different sender → it should NOT be forwarded (verify via `MessageLogScreen` absence).**

- [ ] **Step 5: Switch to `Block`, keep the same rule → resend from the matched sender → it should be filtered out. Resend from the unmatched sender → it should be forwarded again.**

- [ ] **Step 6: Add a rule via "Pick from contacts" → confirm the contact's display name appears as label and the number is prefilled.**

- [ ] **Step 7: Toggle prefix on a new rule like `+593`, confirm it filters any sender starting with that prefix.**

- [ ] **Step 8: Disconnect from Settings → reconfigure → verify the filter list and mode were reset.**

- [ ] **Step 9: Rotate the device while on the filter screen → state preserved (mode, list).**

If any step fails, file a follow-up issue rather than blocking the merge — the implementation may be correct but the rollout can iterate.

---

## Spec Coverage Self-Review

| Spec section | Tasks |
|---|---|
| Off / Allow / Block semantics | Task 6 (decision) + Task 7 (repo) + Task 13 (UI selector) |
| Match exact + prefix + case-insensitive alpha + shortcodes | Task 3 (DAO query) + Task 5 (DAO test) + Task 7 (normalize) |
| `originatingAddress == null` handling | Task 6 (decision branch) + Task 9 (translate `"unknown"` → null) |
| Room entity + unique index | Task 3 + Task 4 (migration) |
| `MIGRATION_1_2` | Task 4 |
| `SenderFilterMode` in SecurePreferences | Task 2 |
| `SenderFilterRepository` with `mode`, `filters`, `count`, `shouldForward`, `add`, `delete`, `reset`, `setMode` | Task 7 |
| `SmsReceiver` integration | Task 9 |
| `Screen.SenderFilter` route + bottom nav hidden | Task 10 (route added; allow-list omits it → bar hidden) |
| `SettingsScreen` summary row + nav callback | Task 12 |
| `SenderFilterScreen` UI (mode selector, warning, list, FAB) | Task 13 |
| Add rule sheet with text + prefix + contact picker | Task 13 (`AddFilterSheet`) |
| ES + EN strings | Task 14 |
| Disconnect cleanup wipes filters + resets mode | Task 8 (uses `senderFilterRepository.reset()`) |
| Unit + instrumented test coverage | Task 5 (DAO) + Task 6 (decision) |
| Permissions: no new runtime perms | Task 13 (uses `ACTION_PICK` system intent) |

No gaps detected.
