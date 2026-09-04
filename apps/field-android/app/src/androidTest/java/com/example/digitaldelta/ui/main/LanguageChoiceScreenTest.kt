package com.example.digitaldelta.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import com.example.digitaldelta.theme.DigitalDeltaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LanguageChoiceScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun freshInstallOffersBanglaFirstAndContinuesWithSelectedLanguage() {
        var selected by mutableStateOf(false)
        var choseBangla = false
        composeTestRule.setContent {
            DigitalDeltaTheme(darkTheme = false) {
                DigitalDeltaApp(
                    showBootSequence = false,
                    languageSelected = selected,
                    useBangla = true,
                    onLanguageChange = { useBangla ->
                        choseBangla = useBangla
                        selected = true
                    },
                )
            }
        }

        composeTestRule.onNode(hasTestTag("language-bangla")).assertIsDisplayed()
        composeTestRule.onNode(hasTestTag("language-english")).assertIsDisplayed()
        composeTestRule.onNode(hasTestTag("language-bangla")).performClick()

        composeTestRule.onNode(hasTestTag("nav-operations")).assertIsDisplayed()
        assertTrue(choseBangla)
    }
}
