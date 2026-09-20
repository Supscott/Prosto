package com.jestk.browser.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.AppDatabase
import com.jestk.browser.domain.repository.FavoriteRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class FavoriteRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: FavoriteRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        repository = FavoriteRepositoryImpl(db.favoriteDao(), testDispatcher)
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        db.close()
    }

    @Test
    fun addFavorite_createsFavoriteWithPosition() = runTest(testDispatcher) {
        val id1 = repository.addFavorite("Jestk", "https://jestk.com")
        val id2 = repository.addFavorite("DuckDuckGo", "https://duckduckgo.com")

        val favorites = repository.getFavorites().first()
        assertThat(favorites).hasSize(2)
        assertThat(favorites[0].id).isEqualTo(id1)
        assertThat(favorites[0].position).isEqualTo(0)
        assertThat(favorites[1].id).isEqualTo(id2)
        assertThat(favorites[1].position).isEqualTo(1)
    }

    @Test
    fun addFavorite_duplicateUrlReturnsExistingId() = runTest(testDispatcher) {
        val id1 = repository.addFavorite("Jestk", "https://jestk.com")
        val id2 = repository.addFavorite("Jestk Home", "https://jestk.com")

        assertThat(id1).isEqualTo(id2)

        val count = repository.getFavoritesCount()
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun getFavoritesLimited_returnsOnlyUpToLimit() = runTest(testDispatcher) {
        for (i in 0 until 10) {
            repository.addFavorite("Site $i", "https://site$i.com")
        }

        val limited = repository.getFavoritesLimited(6).first()
        assertThat(limited).hasSize(6)
        assertThat(limited.map { it.position }).containsExactly(0, 1, 2, 3, 4, 5).inOrder()
    }

    @Test
    fun isFavorite_returnsCorrectValue() = runTest(testDispatcher) {
        repository.addFavorite("Jestk", "https://jestk.com")

        assertThat(repository.isFavorite("https://jestk.com")).isTrue()
        assertThat(repository.isFavorite("https://unknown.com")).isFalse()
    }

    @Test
    fun deleteFavorite_removesBookmark() = runTest(testDispatcher) {
        val id = repository.addFavorite("Jestk", "https://jestk.com")
        assertThat(repository.getFavoritesCount()).isEqualTo(1)

        repository.deleteFavorite(id)
        assertThat(repository.getFavoritesCount()).isEqualTo(0)
    }

    @Test
    fun updateFavicon_updatesStoredIcon() = runTest(testDispatcher) {
        val id = repository.addFavorite("Jestk", "https://jestk.com", iconUrl = "https://jestk.com/old.ico")
        val before = repository.getFavorites().first()
        assertThat(before[0].iconUrl).isEqualTo("https://jestk.com/old.ico")

        repository.updateFavicon("https://jestk.com", "https://jestk.com/new.png")
        val after = repository.getFavorites().first()
        assertThat(after[0].iconUrl).isEqualTo("https://jestk.com/new.png")
    }
}
