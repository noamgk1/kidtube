package com.noam.kidtube.core

import kotlin.test.Test
import kotlin.test.assertTrue

class SkeletonTest {
    @Test
    fun `core module compiles and runs tests on the JVM`() {
        assertTrue(true)
    }

    @Test
    fun `core module has no android on its classpath`() {
        val onClasspath = runCatching { Class.forName("android.content.Context") }.isSuccess
        assertTrue(!onClasspath, "android.content.Context must not be visible from :core")
    }
}
