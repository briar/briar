package org.briarproject.briar.headless.event

import io.javalin.json.JavalinJson.toJson
import io.javalin.websocket.WsSession
import io.mockk.*
import org.briarproject.bramble.test.ImmediateExecutor
import org.briarproject.briar.api.messaging.PrivateMessageHeader
import org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent
import org.briarproject.briar.headless.ControllerTest
import org.briarproject.briar.headless.messaging.EVENT_PRIVATE_MESSAGE
import org.briarproject.briar.headless.messaging.output
import org.eclipse.jetty.websocket.api.WebSocketException
import org.junit.jupiter.api.Test
import java.io.IOException

internal class WebSocketControllerTest : ControllerTest() {

    private val session1 = mockk<WsSession>()
    private val session2 = mockk<WsSession>()

    private val controller = WebSocketControllerImpl(ImmediateExecutor())

    private val header =
        PrivateMessageHeader(message.id, group.id, timestamp, true, true, true, true)
    private val event = PrivateMessageReceivedEvent(header, contact.id)
    private val outputEvent = OutputEvent(EVENT_PRIVATE_MESSAGE, event.output(text))

    @Test
    fun testSendEvent() {
        val slot = CapturingSlot<String>()

        every { session1.send(capture(slot)) } just Runs

        controller.sessions.add(session1)
        controller.sendEvent(EVENT_PRIVATE_MESSAGE, event.output(text))

        assertJsonEquals(slot.captured, outputEvent)
    }

    @Test
    fun testSendEventIOException() {
        testSendEventException(IOException())
    }

    @Test
    fun testSendEventWebSocketException() {
        testSendEventException(WebSocketException())
    }

    private fun testSendEventException(throwable: Throwable) {
        val slot = CapturingSlot<String>()

        every { session1.send(capture(slot)) } throws throwable
        every { session2.send(capture(slot)) } just Runs

        controller.sessions.add(session1)
        controller.sessions.add(session2)
        controller.sendEvent(EVENT_PRIVATE_MESSAGE, event.output(text))

        verify { session2.send(slot.captured) }
    }

    @Test
    fun testOutputPrivateMessageReceivedEvent() {
        val json = """
        {
            "type": "event",
            "name": "PrivateMessageReceivedEvent",
            "data": ${toJson(header.output(contact.id, text))}
        }
        """
        assertJsonEquals(json, outputEvent)
    }

}
