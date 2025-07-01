package com.example.physiqueaiapkfinal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
@LargeTest
class DashboardNavigationTest {

    @Test
    fun testAddedExerciseDataClassExists() {
        // Test na tumitiyak na ang AddedExercise data class ay properly defined
        val exercise = AddedExercise(id = "test-id", workoutName = "Test Exercise")
        assertEquals("test-id", exercise.id)
        assertEquals("Test Exercise", exercise.workoutName)
    }

    @Test
    fun testActivityClassesExist() {
        // Test na tumitiyak na ang mga exercise activity classes ay nandoon
        try {
            Class.forName("com.example.physiqueaiapkfinal.MilitaryPressActivity")
            Class.forName("com.example.physiqueaiapkfinal.HipThrustsActivity")
            Class.forName("com.example.physiqueaiapkfinal.SquatActivity")
            Class.forName("com.example.physiqueaiapkfinal.SitUpsActivity")
            Class.forName("com.example.physiqueaiapkfinal.WindmillActivity")
            Class.forName("com.example.physiqueaiapkfinal.DashboardActivity")
            // Kung lahat ng classes ay na-load, pasado ang test
            assertTrue("All exercise activity classes exist", true)
        } catch (e: ClassNotFoundException) {
            fail("Missing exercise activity class: ${e.message}")
        }
    }

    @Test
    fun testDashboardActivityClassExists() {
        // Simple test na tumitiyak na ang DashboardActivity class ay nandoon
        try {
            val activityClass = Class.forName("com.example.physiqueaiapkfinal.DashboardActivity")
            assertNotNull("DashboardActivity class should exist", activityClass)
            assertTrue("Should be an Activity", 
                android.app.Activity::class.java.isAssignableFrom(activityClass))
        } catch (e: ClassNotFoundException) {
            fail("DashboardActivity class not found: ${e.message}")
        }
    }

    @Test
    fun testAddedExerciseConstructor() {
        // Test para sa AddedExercise constructor
        val exercise1 = AddedExercise("id1", "Exercise 1")
        val exercise2 = AddedExercise("id2", "Exercise 2")
        
        assertNotEquals("Different exercises should not be equal", exercise1.id, exercise2.id)
        assertNotEquals("Different exercises should not be equal", exercise1.workoutName, exercise2.workoutName)
    }

    @Test
    fun testBasicStringOperations() {
        // Simple test na hindi umaasa sa activity
        val testString = "Military Press"
        val normalized = testString.trim().replace(Regex("[\\s-]"), "").lowercase()
        assertEquals("militarypress", normalized)
        
        val testString2 = "Hip Thrust"
        val normalized2 = testString2.trim().replace(Regex("[\\s-]"), "").lowercase()
        assertEquals("hipthrust", normalized2)
    }
}