package com.nousresearch.hermes.ui

import com.nousresearch.hermes.R
import org.junit.Assert.assertEquals
import org.junit.Test

class NousBackdropSequenceTest {
    @Test
    fun `three anchors each flow through their dedicated transition plate`() {
        assertEquals(
            listOf(
                BackdropFrame(R.drawable.nous_field_orbit, 150_000L),
                BackdropFrame(R.drawable.nous_field_orbit_neural, 12_000L),
                BackdropFrame(R.drawable.nous_field_neural, 150_000L),
                BackdropFrame(R.drawable.nous_field_neural_portal, 12_000L),
                BackdropFrame(R.drawable.nous_field_portal, 150_000L),
                BackdropFrame(R.drawable.nous_field_portal_orbit, 12_000L),
            ),
            BackdropSequence,
        )
    }
}
