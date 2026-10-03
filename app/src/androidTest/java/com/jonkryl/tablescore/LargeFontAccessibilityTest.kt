package com.jonkryl.tablescore

import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** CI sets the real system font scale before this fresh API 24 process is launched. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24, maxSdkVersion = 24)
class LargeFontAccessibilityTest {
    @Test
    fun scoresRemainReadableAndControlsWorkAtTwoHundredPercent() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("The system font scale must be 200% for this case",
                    2.0f, activity.resources.configuration.fontScale, 0.05f)
            }
            assertReadableScore(R.id.score_value_0, "3")
            assertReadableScore(R.id.score_value_1, "-1")
            onView(withId(R.id.score_plus_0)).perform(scrollTo()).check { view, error ->
                if (error != null) throw error
                val control = requireNotNull(view)
                val minimumTouchSize = control.resources.displayMetrics.density * 48f
                assertTrue("Score control must be at least 48 dp wide", control.width >= minimumTouchSize)
                assertTrue("Score control must be at least 48 dp tall", control.height >= minimumTouchSize)
            }.perform(click())
            assertReadableScore(R.id.score_value_0, "4")
            onView(withId(R.id.undo_button)).perform(click())
            assertReadableScore(R.id.score_value_0, "3")
            assertReadableScore(R.id.score_value_1, "-1")
            saveScreenshot("03-large-font-200-percent-api-24.png")
        }
    }

    private fun assertReadableScore(id: Int, expected: String) {
        onView(withId(id)).perform(scrollTo()).check { view, error ->
            if (error != null) throw error
            val score = requireNotNull(view) as TextView
            assertEquals(expected, score.text.toString())
            val layout = requireNotNull(score.layout)
            assertEquals("The complete score should occupy one line", 1, layout.lineCount)
            val availableWidth = score.width - score.compoundPaddingLeft - score.compoundPaddingRight
            val availableHeight = score.height - score.compoundPaddingTop - score.compoundPaddingBottom
            assertTrue("Score digits must fit horizontally", layout.getLineWidth(0) <= availableWidth + 1f)
            assertTrue("Score digits must fit vertically",
                layout.getLineBottom(0) - layout.getLineTop(0) <= availableHeight)
        }
    }
}
