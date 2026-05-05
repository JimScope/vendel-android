# Sender Filter — Design

**Date:** 2026-05-05
**Status:** Draft
**Owner:** Jimmy Angel Pérez Díaz

## Goal

Allow users to optionally filter which incoming SMS senders get forwarded to the Vendel server. Today the gate is a single on/off toggle (`incomingSmsEnabled`); after this change users can additionally restrict by sender via an allowlist or blocklist.

## Non-goals

- No content-based filtering (body keywords, regex on message text).
- No per-rule scheduling (time windows).
- No import/export of rules.
- No sync to server (rules are device-local).

## User-facing behavior

- New row in `SettingsScreen` labeled "Filtro de remitentes" showing the current mode plus rule count, e.g. `Allow · 12 reglas`.
- Tapping the row navigates to a dedicated `SenderFilterScreen` (bottom navigation hidden on this screen).
- Filter screen shows:
  - Mode selector (Off / Allow / Block) as a `SingleChoiceSegmentedButtonRow`.
  - Conditional warning when mode is `Allow` and the rule list is empty: "Modo Allow con lista vacía bloquea todo".
  - Scrollable `LazyColumn` listing rules.
  - `ExtendedFloatingActionButton` with `+ Agregar` for adding rules.
- Adding a rule opens a `ModalBottomSheet` with:
  - `OutlinedTextField` for the pattern (number, shortcode, or alpha sender).
  - `Switch` for "Coincidencia por prefijo".
  - `OutlinedButton` "Elegir desde contactos" launching the system contact picker.
  - Save / Cancel buttons.
- Each list row shows: source icon, label (or pattern when no label), pattern, and a trailing delete `IconButton`.

## Filter semantics

- `OFF` (default): forward every SMS the existing `incomingSmsEnabled` toggle would have forwarded. No change to current behavior.
- `ALLOW`: forward only when the sender matches at least one rule. Empty list blocks all.
- `BLOCK`: forward unless the sender matches at least one rule. Empty list forwards all (equivalent to `OFF`).

The new check runs *after* the existing `incomingSmsEnabled` check in `SmsReceiver`, so disabling the global toggle still bypasses the filter entirely.

## Match algorithm

Sender from `SmsMessage.originatingAddress` is normalized once: `trim()` + `lowercase(Locale.ROOT)`.

Per-rule:
- `isPrefix == false` (exact): `sender == lower(pattern)`.
- `isPrefix == true` (prefix): `sender.startsWith(lower(pattern))`.

Patterns are stored as the user typed them (preserving original casing for display) and lowercased only at compare time. This works uniformly for E.164 numbers (`+593987654321`), shortcodes (`12345`), and alphanumeric sender IDs (`BANCO`, `Whatsapp`).

`originatingAddress == null` → never matches any rule. In `ALLOW` mode this means the message is filtered out; in `BLOCK` mode it passes through.

## Data model

### Room entity

```kotlin
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

Unique index on (`pattern`, `is_prefix`) prevents duplicate rules.

### DAO

```kotlin
@Dao
interface SenderFilterDao {
    @Query("SELECT * FROM sender_filters ORDER BY created_at DESC")
    fun observeAll(): Flow<List<SenderFilterEntity>>

    @Query("""
        SELECT EXISTS(SELECT 1 FROM sender_filters
        WHERE (is_prefix = 0 AND LOWER(pattern) = :sender)
           OR (is_prefix = 1 AND :sender LIKE LOWER(pattern) || '%'))
    """)
    suspend fun matches(sender: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(filter: SenderFilterEntity): Long

    @Query("DELETE FROM sender_filters WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM sender_filters")
    fun countFlow(): Flow<Int>

    @Query("DELETE FROM sender_filters")
    suspend fun deleteAll()
}
```

`deleteAll()` is wired into the existing disconnect cleanup so logging out wipes filters too.

### Database

`VendelDatabase` is currently at `version = 1` with `exportSchema = false`. Bump to `version = 2`, add `SenderFilterEntity::class` to the `entities` array, add an abstract `senderFilterDao()` accessor, and register `MIGRATION_1_2` on the Room builder:

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sender_filters (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                pattern TEXT NOT NULL,
                is_prefix INTEGER NOT NULL DEFAULT 0,
                label TEXT,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS index_sender_filters_pattern_is_prefix
            ON sender_filters(pattern, is_prefix)
        """.trimIndent())
    }
}
```

No data migration needed.

### Mode preference

`SecurePreferences` gains:

```kotlin
enum class SenderFilterMode { OFF, ALLOW, BLOCK }

var senderFilterMode: SenderFilterMode
    get() = SenderFilterMode.valueOf(
        prefs.getString(KEY_SENDER_FILTER_MODE, null) ?: SenderFilterMode.OFF.name
    )
    set(value) = prefs.edit().putString(KEY_SENDER_FILTER_MODE, value.name).apply()
```

Backed by a `MutableStateFlow` exposed via repository so the UI reacts.

## Repository

```kotlin
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
        if (_mode.value == SenderFilterMode.OFF) return true
        if (rawSender == null) return _mode.value == SenderFilterMode.BLOCK
        val normalized = rawSender.trim().lowercase(Locale.ROOT)
        val matches = dao.matches(normalized)
        return when (_mode.value) {
            SenderFilterMode.ALLOW -> matches
            SenderFilterMode.BLOCK -> !matches
            SenderFilterMode.OFF -> true
        }
    }

    suspend fun add(pattern: String, isPrefix: Boolean, label: String?) {
        dao.insert(
            SenderFilterEntity(
                pattern = pattern.trim(),
                isPrefix = isPrefix,
                label = label?.trim()?.takeIf { it.isNotBlank() }
            )
        )
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
```

## Filter integration

`SmsReceiver` injects the new repository and calls `shouldForward` per group:

```kotlin
@Inject lateinit var senderFilterRepository: SenderFilterRepository

// inside the launched coroutine, replacing the existing for loop body
for ((sender, parts) in grouped) {
    if (!senderFilterRepository.shouldForward(sender)) {
        if (BuildConfig.DEBUG) Log.d(TAG, "Filtered out: $sender")
        continue
    }
    val body = parts.joinToString("") { it.messageBody ?: "" }
    val timestamp = Instant.ofEpochMilli(parts.first().timestampMillis).toString()
    if (BuildConfig.DEBUG) Log.d(TAG, "Incoming SMS from $sender")
    smsRepository.reportIncoming(sender, body, timestamp)
}
```

## UI components

### Settings row (replaces nothing — added as a new card)

`SettingsScreen` gains a new card after the SMS card. Compact row:

- Title: `Filtro de remitentes`
- Subtitle: dynamically `"Desactivado"` / `"Allow · N reglas"` / `"Block · N reglas"`
- Trailing chevron `Icons.AutoMirrored.Filled.KeyboardArrowRight`
- Whole card clickable → invokes `onNavigateToSenderFilter`

`SettingsScreen` signature gains a new param:

```kotlin
fun SettingsScreen(
    onDisconnect: () -> Unit,
    onNavigateToSenderFilter: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
)
```

`VendelNavigation.kt` wires it via `navController.navigate(Screen.SenderFilter.route)`.

### SenderFilterScreen

New file `ui/filter/SenderFilterScreen.kt` + `SenderFilterViewModel.kt` under a new `ui/filter` package.

Layout:

- `Scaffold` with `TopAppBar(title = "Filtro de remitentes", navigationIcon = back arrow)` and a `floatingActionButton = ExtendedFloatingActionButton(text = "Agregar", icon = Icons.Default.Add, onClick = openSheet)`.
- Body:
  - `SingleChoiceSegmentedButtonRow` with three `SegmentedButton`s for Off / Allow / Block. Selected uses `VendelBrand` accent; unselected uses Material defaults.
  - Conditional warning `Card(containerColor = VendelBrandTint)` when `mode == ALLOW && filters.isEmpty()`.
  - `LazyColumn` of rules. Each row:
    - Leading icon: `Icons.Default.Person` if `label != null`, `Icons.Default.Phone` for numeric prefix entries, `Icons.Default.Storefront` for alpha exact entries (heuristic: alpha = `!pattern.any { it.isDigit() || it == '+' }`).
    - Title: `label ?: pattern`
    - Supporting: `pattern` (only when label is present) + descriptor (`Prefix`, `Alfa, exact`, `Exact`).
    - Trailing: `IconButton(Icons.Default.Close)` for delete.
    - `HorizontalDivider` between items.

### Add rule bottom sheet

`ModalBottomSheet` controlled by `showAddSheet` state.

- `OutlinedTextField` for pattern with placeholder `"Número o nombre del remitente"`.
- Row: `Switch` + label `"Coincidencia por prefijo"`.
- `HorizontalDivider` with centered text `"O"`.
- `OutlinedButton(Icons.Default.PersonAdd, "Elegir desde contactos")` launching `ActivityResultContracts.PickContact()` (uses `Intent.ACTION_PICK` with `Phone.CONTENT_URI`). On result, query the URI for `Phone.NUMBER` and `Phone.DISPLAY_NAME`, prefill text field, set label.
- Footer row: `TextButton("Cancelar")` + `Button("Guardar")` (disabled when pattern blank). Saving calls `viewModel.add(...)` then closes sheet.

### Bottom navigation visibility

`MainActivity`'s `showBottomBar` is an allow-list of routes; `SenderFilter.route` is intentionally not added, which suffices to hide the bar on the new screen. No predicate change is required beyond declaring the route.

### Strings (ES + EN)

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

EN counterparts mirror the ES copy with translation.

## Permissions

No new runtime permissions. The contact picker uses the system `ACTION_PICK` flow which grants temporary URI access to the chosen contact.

## Disconnect cleanup

`ConfigRepository.disconnect()` (already suspend after the prior change) gains:

```kotlin
senderFilterDao.deleteAll()
securePreferences.senderFilterMode = SenderFilterMode.OFF
```

So logging out wipes filter rules and resets the mode along with everything else.

## Dependency injection

Existing `DatabaseModule` provides `VendelDatabase` and DAOs — extend it with `provideSenderFilterDao(db)`. `SenderFilterRepository` is a Hilt `@Singleton` discovered via constructor injection; no module change needed.

## Testing

Unit tests:
- `SenderFilterRepositoryTest` covering the truth table:
  - mode=OFF → always forward.
  - mode=ALLOW + empty list → never forward.
  - mode=ALLOW + matching exact → forward.
  - mode=ALLOW + matching prefix → forward.
  - mode=ALLOW + non-match → don't forward.
  - mode=BLOCK + empty list → forward.
  - mode=BLOCK + match → don't forward.
  - mode=BLOCK + non-match → forward.
  - sender=null in ALLOW → don't forward; in BLOCK → forward.
  - case-insensitive matching for alpha senders (`BANCO` vs `banco`).
  - prefix matches numeric (`+593` matches `+593987654321`).
- `SenderFilterDaoTest` (instrumented Room) covering `matches()` SQL correctness and the unique-index conflict.

Manual test plan:
- Configure server, send SMS from various senders covering exact number, prefix, shortcode, alpha.
- Toggle mode through Off / Allow / Block, verify forwarding behavior in `MessageLogScreen`.
- Add via contact picker, verify label persists.
- Disconnect, reconfigure → filter list and mode reset.
- Rotate device on filter screen → state preserved.

## Open questions

None outstanding.
