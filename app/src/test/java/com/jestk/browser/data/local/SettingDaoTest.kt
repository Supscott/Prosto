package com.jestk.browser.data.local

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.data.local.dao.SettingDao
import com.jestk.browser.data.local.entity.SettingEntity
import com.jestk.browser.data.local.entity.SettingKeys
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
class SettingDaoTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: AppDatabase
    private lateinit var settingDao: SettingDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.createInMemory(context)
        settingDao = db.settingDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun setAndGetSetting_persistsValue() = runTest {
        settingDao.setSetting(SettingEntity(key = SettingKeys.THEME_MODE, value = "DARK"))

        val value = settingDao.getSetting(SettingKeys.THEME_MODE)
        assertThat(value).isEqualTo("DARK")
    }

    @Test
    fun setSetting_overwritesExistingKey() = runTest {
        settingDao.setSetting(SettingEntity(key = SettingKeys.SEARCH_ENGINE, value = "DUCKDUCKGO"))
        settingDao.setSetting(SettingEntity(key = SettingKeys.SEARCH_ENGINE, value = "GOOGLE"))

        val value = settingDao.getSetting(SettingKeys.SEARCH_ENGINE)
        assertThat(value).isEqualTo("GOOGLE")
    }

    @Test
    fun getSettingFlow_emitsUpdates() = runTest {
        settingDao.setSetting(SettingEntity(key = SettingKeys.AD_BLOCKER_ENABLED, value = "true"))
        val value1 = settingDao.getSettingFlow(SettingKeys.AD_BLOCKER_ENABLED).first()
        assertThat(value1).isEqualTo("true")

        settingDao.setSetting(SettingEntity(key = SettingKeys.AD_BLOCKER_ENABLED, value = "false"))
        val value2 = settingDao.getSettingFlow(SettingKeys.AD_BLOCKER_ENABLED).first()
        assertThat(value2).isEqualTo("false")
    }

    @Test
    fun deleteSetting_removesKey() = runTest {
        settingDao.setSetting(SettingEntity(key = SettingKeys.THEME_MODE, value = "LIGHT"))
        settingDao.deleteSetting(SettingKeys.THEME_MODE)

        val value = settingDao.getSetting(SettingKeys.THEME_MODE)
        assertThat(value).isNull()
    }
}
