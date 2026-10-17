package com.liomix.mikomi.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigatorTest {

    @Test
    fun `selectTopLevel resets back stack to the selected destination`() {
        val navigator = Navigator(HomeDestination)
        navigator.goTo(DetailDestination("anime-1"))
        assertEquals(2, navigator.backStack.size)

        navigator.selectTopLevel(ProfileDestination)

        assertEquals(listOf(ProfileDestination), navigator.backStack.toList())
    }

    @Test
    fun `selectTopLevel on the same top level keeps a single entry`() {
        val navigator = Navigator(HomeDestination)

        navigator.selectTopLevel(HomeDestination)

        assertEquals(listOf(HomeDestination), navigator.backStack.toList())
    }

    @Test
    fun `selectTopLevel drops the previous top level`() {
        val navigator = Navigator(HomeDestination)

        navigator.selectTopLevel(AniMapDestination)
        navigator.selectTopLevel(ProfileDestination)

        assertEquals(listOf(ProfileDestination), navigator.backStack.toList())
    }

    @Test
    fun `goBack pops the last destination`() {
        val navigator = Navigator(HomeDestination)
        navigator.goTo(DetailDestination("anime-1"))

        navigator.goBack()

        assertEquals(listOf(HomeDestination), navigator.backStack.toList())
    }
}
