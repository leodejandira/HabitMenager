package com.example.habitapp.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.habitapp.domain.model.ThemeMode
import com.example.habitapp.domain.model.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.DayOfWeek

class DataStoreUserPreferencesRepositoryTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private fun TestScope.createRepository(): DataStoreUserPreferencesRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { File(tmpFolder.root, "test_user_preferences.preferences_pb") },
        )
        return DataStoreUserPreferencesRepository(dataStore)
    }

    @Test
    fun `defaults are returned when nothing is stored`() = runTest {
        val repository = createRepository()

        val preferences = repository.preferences.first()

        assertEquals(UserPreferences(), preferences)
    }

    @Test
    fun `theme mode and dynamic color are persisted`() = runTest {
        val repository = createRepository()

        repository.setThemeMode(ThemeMode.DARK)
        repository.setUseDynamicColor(true)

        val preferences = repository.preferences.first()
        assertEquals(ThemeMode.DARK, preferences.themeMode)
        assertTrue(preferences.useDynamicColor)
    }

    @Test
    fun `first day of week can be set and cleared`() = runTest {
        val repository = createRepository()

        repository.setFirstDayOfWeek(DayOfWeek.SUNDAY)
        assertEquals(DayOfWeek.SUNDAY, repository.preferences.first().firstDayOfWeek)

        repository.setFirstDayOfWeek(null)
        assertNull(repository.preferences.first().firstDayOfWeek)
    }

    @Test
    fun `display name is trimmed and length limited`() = runTest {
        val repository = createRepository()
        val longName = "  " + "a".repeat(UserPreferences.MAX_DISPLAY_NAME_LENGTH + 10) + "  "

        repository.setDisplayName("  Leo  ")
        assertEquals("Leo", repository.preferences.first().displayName)

        repository.setDisplayName(longName)
        assertEquals(UserPreferences.MAX_DISPLAY_NAME_LENGTH, repository.preferences.first().displayName.length)
    }

    @Test
    fun `changes are emitted to existing collectors`() = runTest {
        val repository = createRepository()
        assertFalse(repository.preferences.first().useDynamicColor)

        repository.setUseDynamicColor(true)

        assertTrue(repository.preferences.first().useDynamicColor)
    }
}
