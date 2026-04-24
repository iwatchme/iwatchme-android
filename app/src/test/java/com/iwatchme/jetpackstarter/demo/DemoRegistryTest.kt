package com.iwatchme.jetpackstarter.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoRegistryTest {

    @Test
    fun demoRoutesAreUnique() {
        val routes = DemoRegistry.demos.map { it.route }
        assertEquals(routes.size, routes.distinct().size)
    }

    @Test
    fun demosAreNotEmpty() {
        assertTrue(DemoRegistry.demos.isNotEmpty())
    }
}
