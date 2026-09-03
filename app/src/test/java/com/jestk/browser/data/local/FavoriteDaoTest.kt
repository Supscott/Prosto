package com.jestk.browser.data.local

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.dao.FavoriteDao
import com.jestk.browser.data.local.entity.FavoriteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class FavoriteDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var favoriteDao: FavoriteDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        favoriteDao = db.favoriteDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndQueryFavorites_maintainsPositionOrder() = runTest {
        val fav0 = FavoriteEntity(url = "https://jestk.com", title = "Jestk", position = 0)
        val fav1 = FavoriteEntity(url = "https://duckduckgo.com", title = "DuckDuckGo", position = 1)
        val fav2 = FavoriteEntity(url = "https://github.com", title = "GitHub", position = 2)

        favoriteDao.insertFavorite(fav1)
        favoriteDao.insertFavorite(fav0)
        favoriteDao.insertFavorite(fav2)

        val favorites = favoriteDao.getAllFavoritesFlow().first()
        assertThat(favorites.map { it.url }).containsExactly(
            "https://jestk.com",
            "https://duckduckgo.com",
            "https://github.com"
        ).inOrder()
    }

    @Test
    fun getFavoritesLimitedFlow_respectsLimit() = runTest {
        for (i in 0 until 10) {
            favoriteDao.insertFavorite(
                FavoriteEntity(url = "https://site$i.com", title = "Site $i", position = i)
            )
        }

        val limited = favoriteDao.getFavoritesLimitedFlow(limit = 6).first()
        assertThat(limited).hasSize(6)
        assertThat(limited.map { it.position }).containsExactly(0, 1, 2, 3, 4, 5).inOrder()
    }

    @Test
    fun isFavorite_returnsCorrectStatus() = runTest {
        favoriteDao.insertFavorite(FavoriteEntity(url = "https://jestk.com", title = "Jestk"))

        assertThat(favoriteDao.isFavorite("https://jestk.com")).isTrue()
        assertThat(favoriteDao.isFavorite("https://unknown.com")).isFalse()
    }

    @Test
    fun deleteFavoriteById_removesItem() = runTest {
        val id = favoriteDao.insertFavorite(FavoriteEntity(url = "https://jestk.com", title = "Jestk"))
        assertThat(favoriteDao.getFavoritesCount()).isEqualTo(1)

        favoriteDao.deleteFavoriteById(id)
        assertThat(favoriteDao.getFavoritesCount()).isEqualTo(0)
    }
}
