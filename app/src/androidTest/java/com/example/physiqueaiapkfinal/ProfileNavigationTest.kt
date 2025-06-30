package com.example.physiqueaiapkfinal

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.hamcrest.core.AllOf.allOf
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation test na tinitiyak na kapag pinindot ang Profile sa settings menu
 * ay magbubukas ito ng ProfileActivity.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ProfileNavigationTest {

    @Before
    fun setUp() {
        // I-init ang Espresso Intents upang mahuli ang outgoing intents
        Intents.init()
        // Launch DashboardActivity
        ActivityScenario.launch(DashboardActivity::class.java)
    }

    @After
    fun tearDown() {
        // I-release ang Intents upang hindi makaapekto sa ibang tests
        Intents.release()
    }

    @Test
    fun clickingProfileMenuOpensProfileActivity() {
        // Buksan ang gear button
        onView(withId(R.id.btnProfileMenu)).perform(click())
        // Piliin ang "Profile" mula sa popup
        onView(allOf(withText("Profile"))).perform(click())
        // Suriin na nag-launch ang ProfileActivity
        Intents.intended(hasComponent(ProfileActivity::class.java.name))
    }
} 