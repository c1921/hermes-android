package com.nousresearch.hermes.ui

import com.nousresearch.hermes.ui.navigation.HermesDestinationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WorkspaceFilesNavigationTest {
    @Test
    fun `files destination keeps originating scope and Users path`() {
        val origin = HermesDestinationRoute.Chats(
            backendId = "backend-1",
            profileId = "default",
            sessionId = "session-1",
        )
        val files = HermesDestinationRoute.Artifacts(
            backendId = origin.backendId,
            profileId = origin.profileId,
            filePath = "/Users",
        )

        assertEquals("backend-1", files.backendId)
        assertEquals("default", files.profileId)
        assertEquals("/Users", files.filePath)
    }

    @Test
    fun `header and system back exit files instead of requesting server parent`() {
        val selected = workspaceFilesBackTarget(
            previewOpen = false,
            parentAvailable = true,
            exitAvailable = true,
            atServerRootBoundary = true,
        )

        // The server parent may be `/` or expose `.VolumeIcon.icns`; neither is
        // requested when the originating route can handle the exit.
        assertEquals(
            WorkspaceFilesBackTarget.EXIT_FILES,
            selected,
        )
        assertNotEquals(WorkspaceFilesBackTarget.OPEN_PARENT, selected)
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

    @Test
    fun `nested directory back opens its parent before leaving files`() {
        assertEquals(
            WorkspaceFilesBackTarget.OPEN_PARENT,
            workspaceFilesBackTarget(
                previewOpen = false,
                parentAvailable = true,
                exitAvailable = true,
            ),
        )
    }

    @Test
    fun `root boundary remains traversable when files has no exit callback`() {
        assertEquals(
            WorkspaceFilesBackTarget.OPEN_PARENT,
            workspaceFilesBackTarget(
                previewOpen = false,
                parentAvailable = true,
                exitAvailable = false,
                atServerRootBoundary = true,
            ),
        )
    }
}
