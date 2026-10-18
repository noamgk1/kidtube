package com.noam.kidtube.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasswordHasherTest {

    // Keep unit tests fast; production uses PasswordHasher.ITERATIONS.
    private val fast = 1_000

    @Test
    fun `the correct password verifies`() {
        val settings = PasswordHasher.install(Settings(), "sod1234".toCharArray(), fast)
        assertTrue(PasswordHasher.verify(settings, "sod1234".toCharArray(), fast))
    }

    @Test
    fun `a wrong password does not verify`() {
        val settings = PasswordHasher.install(Settings(), "sod1234".toCharArray(), fast)
        assertFalse(PasswordHasher.verify(settings, "sod1235".toCharArray(), fast))
    }

    @Test
    fun `an unset password never verifies, not even against an empty string`() {
        assertFalse(PasswordHasher.verify(Settings(), "".toCharArray(), fast))
        assertFalse(PasswordHasher.verify(Settings(), "anything".toCharArray(), fast))
    }

    @Test
    fun `the same password under two installs yields different hashes`() {
        val a = PasswordHasher.install(Settings(), "same".toCharArray(), fast)
        val b = PasswordHasher.install(Settings(), "same".toCharArray(), fast)
        assertNotEquals(a.passwordSaltBase64, b.passwordSaltBase64, "salt must be random")
        assertNotEquals(a.passwordHashBase64, b.passwordHashBase64)
    }

    @Test
    fun `the plaintext password is never stored anywhere in settings`() {
        val settings = PasswordHasher.install(Settings(), "sod1234".toCharArray(), fast)
        assertFalse(settings.toString().contains("sod1234"))
    }

    @Test
    fun `clearing removes both salt and hash`() {
        val cleared = PasswordHasher.clear(
            PasswordHasher.install(Settings(), "x".toCharArray(), fast),
        )
        assertNull(cleared.passwordSaltBase64)
        assertNull(cleared.passwordHashBase64)
    }
}
