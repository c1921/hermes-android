package com.nousresearch.hermes.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechTextTest {
    @Test
    fun `read aloud strips code urls emoji and markdown like Hermes Desktop`() {
        val input = """
            Thinking...
            ## Result

            Read [the guide](https://example.com) and `inline code`. 🎉

            ```kotlin
            println("do not read this")
            ```
        """.trimIndent()

        assertEquals("Result. Read the guide and inline code. .", sanitizeTextForSpeech(input))
    }

    @Test
    fun `read aloud rejoins wrapped words and preserves ordinary speech`() {
        assertEquals("Hermes Android is ready.", sanitizeTextForSpeech("Hermes And-\nroid is ready."))
    }
}
