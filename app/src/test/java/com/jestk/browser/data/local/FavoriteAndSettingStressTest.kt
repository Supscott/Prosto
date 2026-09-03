package com.jestk.browser.data.local

import android.content.Context
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.dao.FavoriteDao
import com.jestk.browser.data.local.dao.SettingDao
import com.jestk.browser.data.local.entity.FavoriteEntity
import com.jestk.browser.data.local.entity.SettingEntity
import com.jestk.browser.data.local.entity.SettingKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.Date

@RunWith(RobolectricTestRunner::class)
class FavoriteAndSettingStressTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var favoriteDao: FavoriteDao
    private lateinit var settingDao: SettingDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        favoriteDao = db.favoriteDao()
        settingDao = db.settingDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun favoriteDao_500ItemsReordering_maintainsStrictOrdering() = runTest {
        val count = 500
        val created = (0 until count).map { i ->
            val entity = FavoriteEntity(
                url = "https://favorite-$i.org",
                title = "Fav $i",
                position = i,
                createdAt = i.toLong()
            )
            val id = favoriteDao.insertFavorite(entity)
            entity.copy(id = id)
        }

        assertThat(favoriteDao.getFavoritesCount()).isEqualTo(count)
        assertThat(favoriteDao.getMaxPosition()).isEqualTo(count - 1)

        // Reorder reversed
        val reversed = created.reversed()
        favoriteDao.reorderFavorites(reversed)

        val retrieved = favoriteDao.getAllFavorites()
        assertThat(retrieved).hasSize(count)
        for (i in 0 until count) {
            assertThat(retrieved[i].position).isEqualTo(i)
            assertThat(retrieved[i].url).isEqualTo("https://favorite-${count - 1 - i}.org")
        }

        val limited = favoriteDao.getFavoritesLimitedFlow(limit = 6).first()
        assertThat(limited).hasSize(6)
        assertThat(limited.map { it.position }).containsExactly(0, 1, 2, 3, 4, 5).inOrder()
    }

    @Test
    fun favoriteDao_concurrentAddAndDelete_preservesDatabaseIntegrity() = runTest {
        withContext(Dispatchers.IO) {
            val addJobs = (0 until 50).map { i ->
                async {
                    favoriteDao.insertFavorite(
                        FavoriteEntity(
                            url = "https://concurrent-fav-$i.com",
                            title = "Title $i",
                            position = i
                        )
                    )
                }
            }
            val ids = addJobs.awaitAll()

            val deleteJobs = ids.take(25).map { id ->
                async {
                    favoriteDao.deleteFavoriteById(id)
                }
            }
            deleteJobs.awaitAll()
        }

        val remainingCount = favoriteDao.getFavoritesCount()
        assertThat(remainingCount).isEqualTo(25)
    }

    @Test
    fun favoriteDao_isFavorite_matchesAccurately() = runTest {
        favoriteDao.insertFavorite(FavoriteEntity(url = "https://jestk.com/home", title = "Jestk"))

        assertThat(favoriteDao.isFavorite("https://jestk.com/home")).isTrue()
        assertThat(favoriteDao.isFavorite("https://jestk.com/")).isFalse()
        assertThat(favoriteDao.isFavorite("https://jestk.com")).isFalse()
        assertThat(favoriteDao.isFavorite("")).isFalse()

        favoriteDao.deleteFavoriteByUrl("https://jestk.com/home")
        assertThat(favoriteDao.isFavorite("https://jestk.com/home")).isFalse()
    }

    @Test
    fun settingDao_concurrentSetAndGet_maintainsConsistency() = runTest {
        withContext(Dispatchers.IO) {
            val jobs = (0 until 50).map { i ->
                async {
                    val key = "custom_key_${i % 5}"
                    val value = "value_${i}_" + "x".repeat(100)
                    settingDao.setSetting(SettingEntity(key = key, value = value))
                }
            }
            jobs.awaitAll()
        }

        val allSettings = settingDao.getAllSettings()
        assertThat(allSettings).hasSize(5)
    }

    @Test
    fun settingDao_adversarialValues_jsonAndMultilineAndUnicode() = runTest {
        val complexJson = """{"theme":"DARK","flags":{"adblock":true,"searchEngine":"DUCKDUCKGO"},"tags":["fast","secure"]}"""
        val multilineVal = "Line 1\nLine 2\r\nLine 3\tTabbed\uD83D\uDE80"

        settingDao.setSetting(SettingEntity(key = "complex_json", value = complexJson))
        settingDao.setSetting(SettingEntity(key = "multiline", value = multilineVal))

        val retrievedJson = settingDao.getSetting("complex_json")
        val retrievedMultiline = settingDao.getSetting("multiline")

        assertThat(retrievedJson).isEqualTo(complexJson)
        assertThat(retrievedMultiline).isEqualTo(multilineVal)
    }

    @Test
    fun converters_stressAndEdgeCases() {
        val converters = Converters()

        // String list converter
        assertThat(converters.fromStringList(null)).isEmpty()
        assertThat(converters.fromStringList("")).isEmpty()
        assertThat(converters.stringListToString(null)).isNull()
        assertThat(converters.stringListToString(emptyList())).isNull()

        val listWithSpecialChars = listOf("item1", "item with ||| delimiter", "item3")
        val serialized = converters.stringListToString(listWithSpecialChars)
        val deserialized = converters.fromStringList(serialized)
        assertThat(deserialized).isNotEmpty()

        // URI converter
        assertThat(converters.fromUriString(null)).isNull()
        assertThat(converters.uriToString(null)).isNull()

        val testUri = Uri.parse("https://jestk.com/search?q=test%20query&lang=en#heading")
        val uriStr = converters.uriToString(testUri)
        val parsedUri = converters.fromUriString(uriStr)
        assertThat(parsedUri).isEqualTo(testUri)

        // Date / Timestamp converter
        assertThat(converters.fromTimestamp(null)).isNull()
        assertThat(converters.dateToTimestamp(null)).isNull()

        val date0 = Date(0L)
        assertThat(converters.dateToTimestamp(date0)).isEqualTo(0L)
        assertThat(converters.fromTimestamp(0L)).isEqualTo(date0)

        val dateLarge = Date(1700000000000L)
        assertThat(converters.dateToTimestamp(dateLarge)).isEqualTo(1700000000000L)
        assertThat(converters.fromTimestamp(1700000000000L)).isEqualTo(dateLarge)
    }
}
