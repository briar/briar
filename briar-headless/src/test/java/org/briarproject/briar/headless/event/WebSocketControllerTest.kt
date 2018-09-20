package org.briarproject.briar.headless.event

import io.javalin.json.JavalinJson.toJson
import io.javalin.websocket.WsSession
import io.mockk.*
import org.briarproject.briar.api.messaging.PrivateMessageHeader
import org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent
import org.briarproject.briar.headless.ControllerTest
import org.briarproject.briar.headless.messaging.EVENT_PRIVATE_MESSAGE
import org.briarproject.briar.headless.messaging.output
import org.junit.jupiter.api.Test

internal class WebSocketControllerTest : ControllerTest() {

    private val session = mockk<WsSession>()
    private val controller = WebSocketControllerImpl()

    private val header =
        PrivateMessageHeader(message.id, group.id, timestamp, true, true, true, true)
    private val event = PrivateMessageReceivedEvent(header, contact.id)
    private val outputEvent = OutputEvent(EVENT_PRIVATE_MESSAGE, event.output(body))

    @Test
    fun testSessionSend() {
        val slot = CapturingSlot<String>()

        every { session.send(capture(slot)) } just Runs

        controller.sessions.add(session)
        controller.sendEvent(EVENT_PRIVATE_MESSAGE, event.output(body))

        assertJsonEquals(slot.captured, outputEvent)
    }

    @Test
    fun testOutputPrivateMessageReceivedEvent() {
        val json = """
        {
            "type": "event",
            "name": "org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent",
            "data": ${toJson(header.output(contact.id, body))}
        }
        """
        assertJsonEquals(json, outputEvent)
    }

}
