package com.jonkryl.tablescore

import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Root
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jonkryl.tablescore.core.ScoreJson
import com.jonkryl.tablescore.core.ScoreRepository
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real controls + Activity.recreate; no repository mutation or private UI callback shortcut. */
@RunWith(AndroidJUnit4::class)
class NewGameDraftRecreationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val nameIds = intArrayOf(R.id.player_name_input_0, R.id.player_name_input_1,
        R.id.player_name_input_2, R.id.player_name_input_3, R.id.player_name_input_4,
        R.id.player_name_input_5, R.id.player_name_input_6, R.id.player_name_input_7)

    @Before
    fun guardOwnedDemoEmulatorBeforeFixtures() {
        assertEquals("true", InstrumentationRegistry.getArguments().getString("ownedFirstMacEmulator"))
        assertEquals("com.jonkryl.tablescore", context.packageName)
        assertTrue(BuildConfig.DEBUG)
        assertEquals("demo-banner-yandex", BuildConfig.YANDEX_BANNER_ID)
        assertEquals(36, Build.VERSION.SDK_INT)
        assertTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assertTrue(Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("emulator") ||
            Build.FINGERPRINT.contains("/sdk_gphone64_arm64/"))
        listOf(ScoreRepository.FILE_NAME, ScoreRepository.FILE_NAME + ".bak",
            ScoreRepository.FILE_NAME + ".new").forEach {
            val fixture = File(context.filesDir, it)
            assertTrue(!fixture.exists() || fixture.delete())
        }
    }

    @Test
    fun eightRawNamesAndHiddenDraftsSurviveTwoRecreationsUntilOneExplicitStart() {
        val names = listOf("  Ира  ", "Omar", "Maya", "Noah", "Kai", "Léa", "Bo", "最後")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openNewGame()
            field(R.id.player_count_input, "8")
            names.forEachIndexed { index, raw -> field(nameIds[index], raw) }
            assertTrue(ScoreRepository(context).state.games.isEmpty())
            assertEquals(null, savedBytes())

            field(R.id.player_count_input, "4")
            scenario.recreate()
            draftText(R.id.player_count_input, "4")
            names.take(4).forEachIndexed { index, raw -> draftText(nameIds[index], raw) }
            // Reducing the visible count must hide, rather than erase, the other four drafts.
            field(R.id.player_count_input, "8")
            names.forEachIndexed { index, raw -> draftText(nameIds[index], raw) }
            scenario.recreate()
            draftText(R.id.player_count_input, "8")
            names.forEachIndexed { index, raw -> draftText(nameIds[index], raw) }
            assertEquals("Recreation must not create a saved game", null, savedBytes())

            startDraft()
            val saved = ScoreJson.decode(requireNotNull(savedBytes()))
            assertEquals("One explicit Start must create exactly one game", 1, saved.state.games.size)
            val game = requireNotNull(saved.state.activeGame)
            assertEquals(names.map { it.trim() }, game.players.map { it.name })
            assertEquals(8, game.players.map { it.id }.distinct().size)
            assertEquals(1, game.rounds.size)
            assertTrue(game.rounds.single().points.values.all { it == 0L })
            assertTrue("The single commit's undo must be the prior empty state",
                requireNotNull(saved.undoState).games.isEmpty())
            scenario.recreate()
            onView(withId(R.id.player_count_input)).check(doesNotExist())
            assertEquals(1, ScoreRepository(context).state.games.size)
        }
    }

    @Test
    fun explicitCancelDiscardsRestoredDraftWithoutSavingOrReopeningIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openNewGame()
            field(R.id.player_count_input, "3")
            field(nameIds[0], "Cancelled draft")
            scenario.recreate()
            draftText(R.id.player_count_input, "3")
            draftText(nameIds[0], "Cancelled draft")
            onView(withId(android.R.id.button2)).inRoot(dialog(R.string.new_game)).perform(click())
            assertEquals(null, savedBytes())
            scenario.recreate()
            onView(withId(R.id.player_count_input)).check(doesNotExist())
            assertEquals(null, savedBytes())
            openNewGame()
            draftText(R.id.player_count_input, "2")
            draftText(nameIds[0], context.getString(R.string.player_default, 1))
            draftText(nameIds[1], context.getString(R.string.player_default, 2))
            onView(withId(android.R.id.button2)).inRoot(dialog(R.string.new_game)).perform(click())
            assertTrue(ScoreRepository(context).state.games.isEmpty())
            assertEquals(null, savedBytes())
        }
    }

    @Test
    fun recreationKeepsRunningGameAndPendingConfirmationUntilExplicitReplacementAndUndo() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openNewGame()
            field(nameIds[0], "Original Maya")
            field(nameIds[1], "Original Omar")
            startDraft()
            repeat(2) { onView(withId(R.id.score_plus_0)).perform(scrollTo(), click()) }
            val before = requireNotNull(savedBytes())
            val previous = ScoreJson.decode(before).state
            val oldGame = requireNotNull(previous.activeGame)
            assertEquals(2L, oldGame.total(oldGame.players[0].id))

            openNewGame()
            field(nameIds[0], "Replacement Ira")
            field(nameIds[1], "Replacement Leo")
            startDraft()
            onView(withText(context.getString(R.string.replace_title)))
                .inRoot(dialog(R.string.replace_title)).check(matches(isDisplayed()))
            assertEquals(before, savedBytes())
            scenario.recreate()
            onView(withText(context.getString(R.string.replace_title)))
                .inRoot(dialog(R.string.replace_title)).check(matches(isDisplayed()))
            assertEquals("Restoring a confirmation must never commit replacement", before, savedBytes())

            onView(withId(android.R.id.button2)).inRoot(dialog(R.string.replace_title)).perform(click())
            draftText(nameIds[0], "Replacement Ira")
            draftText(nameIds[1], "Replacement Leo")
            assertEquals(before, savedBytes())
            startDraft()
            onView(withId(android.R.id.button1)).inRoot(dialog(R.string.replace_title)).perform(click())
            val replacement = ScoreJson.decode(requireNotNull(savedBytes()))
            assertEquals(2, replacement.state.games.size)
            val active = requireNotNull(replacement.state.activeGame)
            assertNotEquals(oldGame.id, active.id)
            assertEquals(listOf("Replacement Ira", "Replacement Leo"), active.players.map { it.name })
            val historical = replacement.state.games.single { it.id == oldGame.id }
            assertTrue(historical.isFinished)
            assertEquals(oldGame.players, historical.players)
            assertEquals(oldGame.rounds, historical.rounds)
            assertEquals(previous, replacement.undoState)
            onView(withId(R.id.player_count_input)).check(doesNotExist())
            onView(withId(R.id.undo_button)).perform(scrollTo(), click())
            assertEquals("One undo restores the exact unfinished prior game", previous, ScoreRepository(context).state)
            assertFalse(requireNotNull(ScoreRepository(context).activeGame).isFinished)
        }
    }

    private fun dialog(title: Int): Matcher<Root> = allOf(isDialog(),
        withDecorView(hasDescendant(withText(context.getString(title)))))

    private fun openNewGame() {
        onView(withId(R.id.new_game_button)).perform(scrollTo(), click())
    }

    private fun field(id: Int, value: String) {
        onView(withId(id)).inRoot(dialog(R.string.new_game))
            .perform(scrollTo(), replaceText(value), closeSoftKeyboard())
    }

    private fun draftText(id: Int, value: String) {
        onView(withId(id)).inRoot(dialog(R.string.new_game))
            .perform(scrollTo()).check(matches(withText(value)))
    }

    private fun startDraft() {
        onView(withId(R.id.start_game_button)).inRoot(dialog(R.string.new_game))
            .perform(scrollTo(), click())
    }

    private fun savedBytes(): String? = File(context.filesDir, ScoreRepository.FILE_NAME)
        .takeIf { it.exists() }?.readText(Charsets.UTF_8)
}
