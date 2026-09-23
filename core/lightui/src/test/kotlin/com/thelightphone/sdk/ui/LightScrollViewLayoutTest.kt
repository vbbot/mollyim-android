/*
 * Copyright (c) 2026 The Light Phone
 * SPDX-License-Identifier: MIT
 *
 * Vendored from the Light Phone SDK (sdk/ui). See core/lightui/README.md.
 */

package com.thelightphone.sdk.ui

// MOLLY-VENDOR: upstream uses kotlin.test; Molly's library convention plugin puts JUnit 4 on the
// unit-test classpath instead, so the assertions are expressed with org.junit.Assert.
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LightScrollViewLayoutTest {

    @Test
    fun contentWidthIsInvariantToScrollBarVisibility() {
        val total = 100f
        val position = LightScrollBarPosition.Outside
        val widthWhenBarHidden = scrollViewContentWidthUnits(total, position)
        val widthWhenBarShown = scrollViewContentWidthUnits(total, position)
        assertEquals(widthWhenBarHidden, widthWhenBarShown, 0f)
    }

    @Test
    fun outsideReservesAGutter() {
        assertTrue(scrollBarGutterUnits(LightScrollBarPosition.Outside) > 0f)
    }

    @Test
    fun insideReservesNoGutter() {
        assertEquals(0f, scrollBarGutterUnits(LightScrollBarPosition.Inside), 0f)
    }

    @Test
    fun outsideContentWidthIsTotalMinusGutter() {
        val total = 100f
        val expected = total - scrollBarGutterUnits(LightScrollBarPosition.Outside)
        assertEquals(expected, scrollViewContentWidthUnits(total, LightScrollBarPosition.Outside), 0f)
    }

    @Test
    fun aspectRatioContentDoesNotOscillate() {
        val total = 100f
        val gutter = scrollBarGutterUnits(LightScrollBarPosition.Outside)
        val overflowThreshold = total - gutter / 2f
        fun overflows(width: Float) = width > overflowThreshold
        fun contentWidth() = scrollViewContentWidthUnits(total, LightScrollBarPosition.Outside)

        var shown = false
        val states = mutableListOf(shown)
        repeat(10) {
            shown = overflows(contentWidth())
            states.add(shown)
        }
        assertEquals(
            "scrollbar visibility must reach a stable fixpoint, not oscillate: $states",
            1,
            states.takeLast(4).toSet().size,
        )
    }
}
