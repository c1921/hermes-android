package com.nousresearch.hermes.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceFilesNavigationTest {
    @Test
    fun `phone back exits files instead of traversing the server parent`() {
        assertEquals(
            WorkspaceFilesBackTarget.EXIT_FILES,
            workspaceFilesBackTarget(
                previewOpen = false,
                parentAvailable = true,
                exitAvailable = true,
            ),
        )
    }

    @Test
    fun `back closes an open preview before leaving files`() {
        assertEquals(
            WorkspaceFilesBackTarget.CLOSE_PREVIEW,
            workspaceFilesBackTarget(
                previewOpen = true,
                parentAvailable = true,
                exitAvailable = true,
            ),
        )
    }

    @Test
    fun `wide layout back traverses the parent when there is no files exit`() {
        assertEquals(
            WorkspaceFilesBackTarget.OPEN_PARENT,
            workspaceFilesBackTarget(
                previewOpen = false,
                parentAvailable = true,
                exitAvailable = false,
            ),
        )
    }
}
