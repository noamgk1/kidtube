package com.noam.kidtube.app.ui

import java.text.NumberFormat
import java.util.Locale

private val HEBREW = Locale.forLanguageTag("he-IL")

/**
 * A count a parent can read at a glance.
 *
 * A subscriber count printed as `47100000` is the one number on the confirmation card
 * that tells a parent they pasted the channel they meant to, and unseparated digits are
 * the hardest possible way to read it.
 */
fun formatCount(value: Long): String =
    NumberFormat.getIntegerInstance(HEBREW).format(value)
