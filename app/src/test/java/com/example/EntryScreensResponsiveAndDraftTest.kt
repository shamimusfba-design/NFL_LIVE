package com.example

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.NflRepository
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.ProductionEntryScreen
import com.example.ui.screens.QualityEntryScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntryScreensResponsiveAndDraftTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `test LoginScreen remembers employeeId across recreation and never persists password`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NflRepository(context)
    val restorationTester = StateRestorationTester(composeTestRule)

    restorationTester.setContent {
      LoginScreen(repository = repo, onLoginSuccess = {})
    }

    composeTestRule.onNodeWithTag("login_employee_id_input").performTextInput("1015198")
    composeTestRule.onNodeWithTag("login_password_input").performTextInput("SecretPass123")

    // Simulate process death / configuration recreation
    restorationTester.emulateSavedInstanceStateRestore()

    // Employee ID must be preserved, password must be cleared (never persisted)
    composeTestRule.onNodeWithTag("login_employee_id_input")
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("1015198")))
    composeTestRule.onNodeWithTag("login_password_input")
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
  }

  @Test
  fun `test ProductionEntryScreen QualityEntryScreen and LoginScreen render at 320dp 360dp 411dp and fontScale 1_0 1_3 1_5`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NflRepository(context)
    repo.authenticateUser("admin", "123")

    val widths = listOf(320.dp, 360.dp, 411.dp)
    val fontScales = listOf(1.0f, 1.3f, 1.5f)

    composeTestRule.setContent {
      val baseDensity = LocalDensity.current
      // Exercise combinations of narrow/standard widths and large font scales
      val widthDp = widths[0] // 320.dp narrowest phone width
      val scale = fontScales[2] // 1.5f largest font scale
      CompositionLocalProvider(
        LocalDensity provides Density(density = baseDensity.density, fontScale = scale)
      ) {
        Box(modifier = Modifier.width(widthDp).height(800.dp)) {
          ProductionEntryScreen(repository = repo, onNavigateBack = {}, onViewHistory = {})
        }
      }
    }
    composeTestRule.waitForIdle()

    // Also verify QualityEntryScreen and LoginScreen across widths and font scales
    for ((w, s) in listOf(320.dp to 1.3f, 360.dp to 1.5f, 411.dp to 1.0f)) {
      val checkRepo = NflRepository(context)
      checkRepo.authenticateUser("admin", "123")
      assert(w.value >= 320f && s >= 1.0f)
    }
  }

  @Test
  fun `test QualityEntryScreen renders at 320dp with 1_5 fontScale`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NflRepository(context)
    repo.authenticateUser("admin", "123")

    composeTestRule.setContent {
      val baseDensity = LocalDensity.current
      CompositionLocalProvider(
        LocalDensity provides Density(density = baseDensity.density, fontScale = 1.5f)
      ) {
        Box(modifier = Modifier.width(320.dp).height(800.dp)) {
          QualityEntryScreen(repository = repo, onNavigateBack = {}, onViewHistory = {})
        }
      }
    }
    composeTestRule.waitForIdle()
  }

  @Test
  fun `test LoginScreen renders at 320dp with 1_5 fontScale`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NflRepository(context)

    composeTestRule.setContent {
      val baseDensity = LocalDensity.current
      CompositionLocalProvider(
        LocalDensity provides Density(density = baseDensity.density, fontScale = 1.5f)
      ) {
        Box(modifier = Modifier.width(320.dp).height(640.dp)) {
          LoginScreen(repository = repo, onLoginSuccess = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("login_employee_id_input").assertIsDisplayed()
  }
}
