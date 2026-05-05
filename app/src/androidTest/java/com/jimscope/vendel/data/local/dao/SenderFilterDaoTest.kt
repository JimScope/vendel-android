package com.jimscope.vendel.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jimscope.vendel.data.local.VendelDatabase
import com.jimscope.vendel.data.local.entity.SenderFilterEntity
import kotlinx.coroutines.flow.first
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
    fun matchIsCaseInsensitiveForAlphaWhenStoredUppercaseSenderLowercase() = runTest {
        dao.insert(SenderFilterEntity(pattern = "BANCO", isPrefix = false))
        assertTrue(dao.matches("banco"))
    }

    @Test
    fun matchIsCaseInsensitiveForAlphaWhenStoredLowercaseSenderUppercase() = runTest {
        dao.insert(SenderFilterEntity(pattern = "banco", isPrefix = false))
        assertTrue(dao.matches("BANCO"))
    }

    @Test
    fun matchIsCaseInsensitiveForAlphaPrefix() = runTest {
        dao.insert(SenderFilterEntity(pattern = "Whats", isPrefix = true))
        assertTrue(dao.matches("WhatsApp"))
        assertTrue(dao.matches("WHATSAPP"))
        assertTrue(dao.matches("whatsapp"))
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
        // No exception, single row remains. Verify via deleteAll then deleteAll again - both succeed.
        dao.deleteAll()
        assertFalse(dao.matches("banco"))
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
    fun exactRulePatternDoesNotPrefixMatch() = runTest {
        dao.insert(SenderFilterEntity(pattern = "BANCO", isPrefix = false))
        assertFalse(dao.matches("bancoplus")) // exact-mode shouldn't prefix-match
    }

    @Test
    fun observeAllOrdersByCreatedAtDesc() = runTest {
        dao.insert(SenderFilterEntity(pattern = "first", isPrefix = false, createdAt = 100))
        dao.insert(SenderFilterEntity(pattern = "second", isPrefix = false, createdAt = 200))
        val items = dao.observeAll().first()
        assertEquals("second", items[0].pattern)
        assertEquals("first", items[1].pattern)
    }
}
