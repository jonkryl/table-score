package com.jonkryl.tablescore

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** CI runs this class in a separate instrumentation invocation after am force-stop. */
@RunWith(AndroidJUnit4::class)
class RestartAndExportTest {
    @Test
    fun restoresAfterProcessDeathAndExportsText() {
        Intents.init()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                onView(withId(R.id.score_value_0)).check(matches(withText("3")))
                onView(withId(R.id.score_value_1)).check(matches(withText("-1")))
                // Undo itself must survive process death, including the last point in the new round.
                onView(withId(R.id.undo_button)).perform(click())
                onView(withId(R.id.score_value_0)).check(matches(withText("2")))
                onView(withId(R.id.score_plus_0)).perform(click())
                intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(ActivityResult(Activity.RESULT_OK, null))
                onView(withId(R.id.share_button)).perform(scrollTo(), click())
                intended(allOf(
                    hasAction(Intent.ACTION_CHOOSER),
                    hasExtra(Intent.EXTRA_INTENT, allOf(
                        hasAction(Intent.ACTION_SEND),
                        hasType("text/plain"),
                        hasExtra(Intent.EXTRA_TEXT, containsString("Maya")),
                        hasExtra(Intent.EXTRA_TEXT, containsString("Leo")),
                        hasExtra(Intent.EXTRA_TEXT, containsString("-1"))
                    ))
                ))
                for (language in listOf("en", "ru")) {
                    scenario.onActivity { activity ->
                        val configuration = Configuration(activity.resources.configuration)
                        configuration.setLocale(Locale(language))
                        @Suppress("DEPRECATION")
                        activity.resources.updateConfiguration(configuration, activity.resources.displayMetrics)
                        @Suppress("DEPRECATION")
                        activity.applicationContext.resources.updateConfiguration(configuration, activity.resources.displayMetrics)
                    }
                    scenario.recreate()
                    onView(withId(R.id.score_value_0)).check(matches(withText("3")))
                    saveScreenshot("02-restored-${language}-api-${Build.VERSION.SDK_INT}.png")
                }
            }
        } finally {
            Intents.release()
        }
    }
}
