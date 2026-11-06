package com.liomix.mikomi.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {

    private fun navigator(start: NavKey = HomeDestination): Navigator =
        Navigator(NavBackStack(start))

    @Test
    fun `navigate pushes a secondary destination`() {
        val navigator = navigator()

        assertTrue(navigator.navigate(DetailDestination("anime-1")))

        assertEquals(
            listOf(HomeDestination, DetailDestination("anime-1")),
            navigator.stack.toList(),
        )
    }

    @Test
    fun `navigate to the same destination twice only pushes once`() {
        val navigator = navigator()

        navigator.navigate(DetailDestination("anime-1"))
        assertFalse(navigator.navigate(DetailDestination("anime-1")))

        // 同一个 NavKey 在栈里出现两次会得到两个相同的 contentKey，
        // NavDisplay 的 decorateEntry 拿它做 key()，两页的状态会搅在一起。
        assertEquals(2, navigator.stack.size)
    }

    @Test
    fun `navigate to a top level destination switches instead of pushing`() {
        val navigator = navigator()

        assertTrue(navigator.navigate(ProfileDestination))

        assertEquals(listOf(ProfileDestination), navigator.stack.toList())
        assertEquals(ProfileDestination, navigator.currentTopLevel)
    }

    @Test
    fun `navigate to the current top level keeps a single entry`() {
        val navigator = navigator()

        assertFalse(navigator.navigate(HomeDestination))

        assertEquals(listOf(HomeDestination), navigator.stack.toList())
    }

    @Test
    fun `selectTopLevel drops the previous top level`() {
        val navigator = navigator()

        navigator.selectTopLevel(AniMapDestination)
        navigator.selectTopLevel(ProfileDestination)

        assertEquals(listOf(ProfileDestination), navigator.stack.toList())
    }

    @Test
    fun `selectTopLevel on a nested stack returns to the tab root`() {
        val navigator = navigator()
        navigator.navigate(DetailDestination("anime-1"))

        navigator.selectTopLevel(HomeDestination)

        assertEquals(listOf(HomeDestination), navigator.stack.toList())
    }

    @Test
    fun `selectTopLevel ignores a non top level destination`() {
        val navigator = navigator()

        assertFalse(navigator.selectTopLevel(DetailDestination("anime-1")))

        assertEquals(listOf(HomeDestination), navigator.stack.toList())
    }

    @Test
    fun `goBack pops the last destination`() {
        val navigator = navigator()
        navigator.navigate(DetailDestination("anime-1"))

        assertTrue(navigator.goBack())

        assertEquals(listOf(HomeDestination), navigator.stack.toList())
    }

    @Test
    fun `goBack at the root reports false so the caller can exit`() {
        val navigator = navigator()

        assertFalse(navigator.goBack())
        assertFalse(navigator.canGoBack)
        assertEquals(listOf(HomeDestination), navigator.stack.toList())
    }

    @Test
    fun `currentTopLevel ignores nested destinations on top of the stack`() {
        val navigator = navigator()
        navigator.selectTopLevel(ProfileDestination)
        navigator.navigate(SettingsDestination)

        assertEquals(SettingsDestination, navigator.current)
        assertEquals(ProfileDestination, navigator.currentTopLevel)
        assertTrue(navigator.canGoBack)
    }
}
