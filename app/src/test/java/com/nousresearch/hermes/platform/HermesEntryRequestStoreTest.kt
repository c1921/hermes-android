package com.nousresearch.hermes.platform

import com.nousresearch.hermes.ui.navigation.HermesDestinationRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesEntryRequestStoreTest {
    @Test
    fun `store is bounded and deduplicates stable request identity`() {
        val store = HermesEntryRequestStore()
        val first = request("one")
        val second = request("two")
        val third = request("three")

        assertTrue(store.enqueue(first))
        assertFalse(store.enqueue(first))
        assertTrue(store.enqueue(second))
        assertTrue(store.enqueue(third))
        assertFalse(store.enqueue(request("four")))
        assertEquals(listOf(first, second, third), store.deliveries.value.map { it.request })
    }

    @Test
    fun `failed request remains visible until retry or discard`() {
        val store = HermesEntryRequestStore()
        store.enqueue(HermesEntryRequest.NewChat())

        store.fail("new-chat", "Could not create chat")
        assertEquals("Could not create chat", store.deliveries.value.single().failureMessage)

        store.retry("new-chat")
        assertNull(store.deliveries.value.single().failureMessage)
        assertEquals(1, store.deliveries.value.single().attempt)

        store.fail("new-chat", "Still unavailable")
        store.discard("new-chat")
        assertTrue(store.deliveries.value.isEmpty())
    }

    @Test
    fun `new process store restores no request payload or authority`() {
        val previousProcess = HermesEntryRequestStore().apply {
            enqueue(
                HermesEntryRequest.ImportDraft(
                    "share:test",
                    SharedContent("share:test", "private draft", emptyList()),
                ),
            )
        }

        val recreatedProcess = HermesEntryRequestStore()

        assertEquals(1, previousProcess.deliveries.value.size)
        assertTrue(recreatedProcess.deliveries.value.isEmpty())
    }

    private fun request(id: String) = HermesEntryRequest.OpenDestination(
        id = id,
        route = HermesDestinationRoute.Chats("backend", "default"),
    )
}
