package com.github.andreyasadchy.xtra.lab

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Sample of the Compose + Robolectric interaction test category (class names end in InteractionTest). */
@RunWith(AndroidJUnit4::class)
class LabHomeInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsTitle() {
        compose.setContent { LabHome() }
        compose.onNodeWithText("Lagoon lab").assertIsDisplayed()
    }
}
