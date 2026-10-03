package com.jonkryl.tablescore

import android.graphics.Bitmap
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Uses real UI actions and saves a game that the next, fresh app process must restore. */
@RunWith(AndroidJUnit4::class)
class TableScoreJourneyTest {
    @Test
    fun scoresRoundsUndoAndPersist() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        listOf("table-score-state-v1.json", "table-score-state-v1.json.bak", "table-score-state-v1.json.new").forEach {
            context.deleteFile(it)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.new_game_button)).perform(click())
            onView(withId(R.id.player_count_input)).perform(replaceText("2"), closeSoftKeyboard())
            onView(withId(R.id.player_name_input_0)).perform(replaceText("Maya"), closeSoftKeyboard())
            onView(withId(R.id.player_name_input_1)).perform(replaceText("Leo"), closeSoftKeyboard())
            onView(withId(R.id.start_game_button)).perform(click())
            onView(withId(R.id.score_value_0)).check(matches(withText("0")))
            onView(withId(R.id.score_value_1)).check(matches(withText("0")))
            repeat(2) { onView(withId(R.id.score_plus_0)).perform(click()) }
            repeat(2) { onView(withId(R.id.score_minus_1)).perform(click()) }
            onView(withId(R.id.score_value_0)).check(matches(withText("2")))
            onView(withId(R.id.score_value_1)).check(matches(withText("-2")))
            onView(withId(R.id.undo_button)).perform(click())
            onView(withId(R.id.score_value_1)).check(matches(withText("-1")))
            onView(withId(R.id.round_button)).perform(scrollTo(), click())
            onView(withId(R.id.score_plus_0)).perform(scrollTo(), click())
            onView(withId(R.id.score_value_0)).check(matches(withText("3")))
            onView(withId(R.id.score_value_1)).check(matches(withText("-1")))
            saveScreenshot("01-live-scores-api-${Build.VERSION.SDK_INT}.png")
            scenario.recreate()
            onView(withId(R.id.score_value_0)).check(matches(withText("3")))
            onView(withId(R.id.score_value_1)).check(matches(withText("-1")))
        }
    }
}

internal fun saveScreenshot(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()
    val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots")
    check(directory.mkdirs() || directory.isDirectory)
    val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
    File(directory, name).outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    screenshot.recycle()
}
