package com.liomix.mikomi.core.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersionBridgeTest {

    @Test
    fun `resetForTopLevelNavigation clears both navigation visibility states`() {
        val bridge = ImmersionBridge()
        bridge.onImmersiveChange(true)
        bridge.onNavBarTempChange(true)

        bridge.resetForTopLevelNavigation()

        assertFalse(bridge.immersive.value)
        assertFalse(bridge.navBarTemp.value)
    }

    @Test
    fun `leaving immersive mode keeps a temporarily revealed navigation bar`() {
        val bridge = ImmersionBridge()
        bridge.onImmersiveChange(true)
        bridge.onNavBarTempChange(true)

        bridge.onImmersiveChange(false)

        assertFalse(bridge.immersive.value)
        assertTrue(bridge.navBarTemp.value)
    }
}
