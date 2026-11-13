package com.themasum.aayna

import com.themasum.aayna.navigation.AaynaRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AaynaRoutesTest {
    @Test
    fun `all navigation routes are unique and non-blank`() {
        val routes =
            listOf(
                AaynaRoutes.SPLASH,
                AaynaRoutes.VIEWFINDER,
                AaynaRoutes.GALLERY,
                AaynaRoutes.SETTINGS,
                AaynaRoutes.DIAGNOSTICS
            )

        assertTrue(routes.all { it.isNotBlank() })
        assertEquals(routes.size, routes.distinct().size)
    }

    @Test
    fun `route names match expected destinations`() {
        assertEquals("splash", AaynaRoutes.SPLASH)
        assertEquals("viewfinder", AaynaRoutes.VIEWFINDER)
        assertEquals("gallery", AaynaRoutes.GALLERY)
        assertEquals("settings", AaynaRoutes.SETTINGS)
        assertEquals("diagnostics", AaynaRoutes.DIAGNOSTICS)
    }
}
