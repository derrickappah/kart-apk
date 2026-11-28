package com.example

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.components.ConnectivityBannerState
import com.example.ui.components.KartBottomChromeDock
import com.example.ui.components.rememberConnectivityBannerState
import com.example.ui.theme.KartTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConnectivityBannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Composable
    private fun TestBottomDockHost(
        isOnline: Boolean,
        showBottomBar: Boolean = true,
        backOnlineDurationMs: Long = 2_500L
    ) {
        KartTheme {
            val bannerState = rememberConnectivityBannerState(
                isOnline = isOnline,
                backOnlineDurationMs = backOnlineDurationMs
            )
            val visibilityState = remember {
                MutableTransitionState(bannerState != ConnectivityBannerState.HIDDEN)
            }
            visibilityState.targetState = bannerState != ConnectivityBannerState.HIDDEN

            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth())
                KartBottomChromeDock(
                    showBottomBar = showBottomBar,
                    currentRoute = "home",
                    unreadMessages = 0,
                    onTabSelected = {},
                    bannerState = bannerState,
                    bannerVisibilityState = visibilityState
                )
            }
        }
    }

    @Test
    fun `initial online state renders bottom nav and hides connectivity banner`() {
        composeRule.setContent {
            TestBottomDockHost(isOnline = true)
        }

        composeRule.onNodeWithTag("kart_bottom_nav").assertIsDisplayed()
        composeRule.onNodeWithTag("connectivity_status_banner").assertDoesNotExist()
        composeRule.onNodeWithText("You're offline").assertDoesNotExist()
        composeRule.onNodeWithText("You're back online").assertDoesNotExist()
    }

    @Test
    fun `offline transition renders full-width banner directly below bottom nav`() {
        var online by mutableStateOf(true)
        composeRule.setContent {
            TestBottomDockHost(isOnline = online)
        }

        online = false
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("kart_bottom_nav").assertIsDisplayed()
        composeRule.onNodeWithTag("connectivity_status_banner").assertIsDisplayed()
        composeRule.onNodeWithText("You're offline").assertIsDisplayed()

        val navBounds = composeRule.onNodeWithTag("kart_bottom_nav").getUnclippedBoundsInRoot()
        val bannerBounds = composeRule.onNodeWithTag("connectivity_status_banner").getUnclippedBoundsInRoot()

        assertTrue(
            "Expected banner top (${bannerBounds.top}) to be directly below bottom nav bottom (${navBounds.bottom})",
            bannerBounds.top.value >= navBounds.bottom.value - 1f
        )
        assertEquals(navBounds.left.value, bannerBounds.left.value, 1f)
        assertEquals(navBounds.right.value, bannerBounds.right.value, 1f)
    }

    @Test
    fun `back online transition shows banner below bottom nav and auto-dismisses after timeout`() {
        var online by mutableStateOf(true)
        composeRule.setContent {
            TestBottomDockHost(isOnline = online, backOnlineDurationMs = 2_500L)
        }

        online = false
        composeRule.waitForIdle()
        composeRule.onNodeWithText("You're offline").assertIsDisplayed()

        online = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("kart_bottom_nav").assertIsDisplayed()
        composeRule.onNodeWithTag("connectivity_status_banner").assertIsDisplayed()
        composeRule.onNodeWithText("You're back online").assertIsDisplayed()

        val navBounds = composeRule.onNodeWithTag("kart_bottom_nav").getUnclippedBoundsInRoot()
        val bannerBounds = composeRule.onNodeWithTag("connectivity_status_banner").getUnclippedBoundsInRoot()
        assertTrue(bannerBounds.top.value >= navBounds.bottom.value - 1f)

        composeRule.mainClock.advanceTimeBy(3_000L)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("connectivity_status_banner").assertDoesNotExist()
        composeRule.onNodeWithText("You're back online").assertDoesNotExist()
    }

    @Test
    fun `rapid flap offline to online to offline cancels auto-dismiss and keeps offline banner`() {
        var online by mutableStateOf(false)
        composeRule.setContent {
            TestBottomDockHost(isOnline = online, backOnlineDurationMs = 2_500L)
        }

        composeRule.onNodeWithText("You're offline").assertIsDisplayed()

        online = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText("You're back online").assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(1_000L)
        online = false
        composeRule.waitForIdle()

        composeRule.mainClock.advanceTimeBy(2_500L)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("connectivity_status_banner").assertIsDisplayed()
        composeRule.onNodeWithText("You're offline").assertIsDisplayed()
        composeRule.onNodeWithText("You're back online").assertDoesNotExist()
    }

    @Test
    fun `sub-screen mode hides bottom nav while still rendering connectivity banner`() {
        var online by mutableStateOf(false)
        composeRule.setContent {
            TestBottomDockHost(isOnline = online, showBottomBar = false, backOnlineDurationMs = 2_500L)
        }

        composeRule.onNodeWithTag("kart_bottom_nav").assertDoesNotExist()
        composeRule.onNodeWithTag("connectivity_status_banner").assertIsDisplayed()
        composeRule.onNodeWithText("You're offline").assertIsDisplayed()

        online = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText("You're back online").assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(3_000L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("connectivity_status_banner").assertDoesNotExist()
    }
}
