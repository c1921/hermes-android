package com.nousresearch.hermes.protocol

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentProtocolModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes image attachment response from Hermes 0 18 2`() {
        val result = json.decodeFromString<ImageAttachResult>(
            """{"attached":true,"path":"/srv/.hermes/images/upload_1.png","count":1,"text":"[User attached image]","bytes":42,"width":8}""",
        )

        assertEquals("/srv/.hermes/images/upload_1.png", result.path)
        assertEquals(42L, result.bytes)
    }

    @Test
    fun `decodes rendered PDF pages from Hermes 0 18 2`() {
        val result = json.decodeFromString<PdfAttachResult>(
            """{"attached":true,"filename":"brief.pdf","pages_attached":2,"pages":[{"path":"/tmp/p1.png","page":1,"width":100},{"path":"/tmp/p2.png","page":2}],"count":2}""",
        )

        assertEquals(2, result.pagesAttached)
        assertEquals(listOf("/tmp/p1.png", "/tmp/p2.png"), result.pages.map { it.path })
    }

    @Test
    fun `decodes file reference without treating server paths as Android paths`() {
        val result = json.decodeFromString<FileAttachResult>(
            """{"attached":true,"name":"notes.txt","path":"/workspace/.hermes/desktop-attachments/notes.txt","ref_path":".hermes/desktop-attachments/notes.txt","ref_text":"@file:.hermes/desktop-attachments/notes.txt","uploaded":true}""",
        )

        assertEquals("@file:.hermes/desktop-attachments/notes.txt", result.refText)
        assertEquals(true, result.uploaded)
    }
}
