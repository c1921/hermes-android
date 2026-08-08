package com.nousresearch.hermes.ui

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveWorkspaceLayoutTest {
    @Test
    fun `compact and medium windows keep modal single pane navigation`() {
        assertEquals(AdaptiveWorkspaceLayout.COMPACT, adaptiveWorkspaceConfiguration(WindowSizeClass(0, 0)).layout)
        assertEquals(AdaptiveWorkspaceLayout.COMPACT, adaptiveWorkspaceConfiguration(WindowSizeClass(600, 0)).layout)
    }

    @Test
    fun `expanded windows expose persistent list detail panes`() {
        assertEquals(AdaptiveWorkspaceLayout.EXPANDED, adaptiveWorkspaceConfiguration(WindowSizeClass(840, 0)).layout)
        assertEquals(AdaptiveWorkspaceLayout.EXPANDED, adaptiveWorkspaceConfiguration(WindowSizeClass(1200, 0)).layout)
        assertFalse(adaptiveWorkspaceConfiguration(WindowSizeClass(840, 0)).supportsSupportingPane)
        assertTrue(adaptiveWorkspaceConfiguration(WindowSizeClass(1200, 0)).supportsSupportingPane)
    }

    @Test
    fun `a book posture partitions expanded navigation and detail around the hinge`() {
        val configuration = adaptiveWorkspaceConfiguration(
            WindowSizeClass(1200, 800),
            verticalHinge = AdaptiveHingeBounds(590.dp, 0.dp, 610.dp, 800.dp),
        )

        assertEquals(AdaptiveWorkspaceLayout.EXPANDED, configuration.layout)
        assertNotNull(configuration.verticalHinge)
        assertFalse(configuration.supportsSupportingPane)
    }

    @Test
    fun `an unsafe fold chooses one non occluded region`() {
        val configuration = adaptiveWorkspaceConfiguration(
            WindowSizeClass(700, 800),
            verticalHinge = AdaptiveHingeBounds(340.dp, 0.dp, 360.dp, 800.dp),
        )

        assertEquals(AdaptiveWorkspaceLayout.COMPACT, configuration.layout)
        assertTrue(configuration.safeContentPadding.calculateRightPadding(LayoutDirection.Ltr) >= 360.dp)
    }
}
