package org.briarproject.briar.headless.messaging

import io.javalin.BadRequestResponse
import io.javalin.Context
import io.javalin.NotFoundResponse
import io.javalin.json.JavalinJson.toJson
import io.mockk.*
import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.bramble.api.db.NoSuchContactException
import org.briarproject.bramble.test.ImmediateExecutor
import org.briarproject.bramble.test.TestUtils.getRandomId
import org.briarproject.bramble.util.StringUtils.getRandomString
import org.briarproject.briar.api.client.SessionId
import org.briarproject.briar.api.introduction.IntroductionRequest
import org.briarproject.briar.api.messaging.*
import org.briarproject.briar.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_TEXT_LENGTH
import org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent
import org.briarproject.briar.headless.ControllerTest
import org.briarproject.briar.headless.event.WebSocketController
import org.briarproject.briar.headless.event.output
import org.briarproject.briar.headless.json.JsonDict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class MessagingControllerImplTest : ControllerTest() {

    private val messagingManager = mockk<MessagingManager>()
    private val conversationManager = mockk<ConversationManager>()
    private val privateMessageFactory = mockk<PrivateMessageFactory>()
    private val webSocketController = mockk<WebSocketController>()
    private val dbExecutor = ImmediateExecutor()

    private val controller = MessagingControllerImpl(
        messagingManager,
        conversationManager,
        privateMessageFactory,
        contactManager,
        webSocketController,
        dbExecutor,
        clock
    )

    private val header =
        PrivateMessageHeader(message.id, group.id, timestamp, true, true, true, true)
    private val sessionId = SessionId(getRandomId())
    private val privateMessage = PrivateMessage(message)

    @Test
    fun list() {
        expectGetContact()
        every { conversationManager.getMessageHeaders(contact.id) } returns listOf(header)
        every { messagingManager.getMessageText(message.id) } returns text
        every { ctx.json(listOf(header.output(contact.id, text))) } returns ctx

        controller.list(ctx)
    }

    @Test
    fun listIntroductionRequest() {
        val request = IntroductionRequest(
            message.id, group.id, timestamp, true, true, false, true, sessionId, author, text,
            false, false
        )

        expectGetContact()
        every { conversationManager.getMessageHeaders(contact.id) } returns listOf(request)
        every { ctx.json(listOf(request.output(contact.id))) } returns ctx

        controller.list(ctx)
    }

    @Test
    fun emptyList() {
        every { ctx.pathParam("contactId") } returns contact.id.int.toString()
        every { contactManager.getContact(contact.id) } returns contact
        every { conversationManager.getMessageHeaders(contact.id) } returns emptyList<PrivateMessageHeader>()
        every { ctx.json(emptyList<Any>()) } returns ctx

        controller.list(ctx)
    }

    @Test
    fun listInvalidContactId() {
        testInvalidContactId { controller.list(ctx) }
    }

    @Test
    fun listNonexistentContactId() {
        testNonexistentContactId { controller.list(ctx) }
    }

    @Test
    fun write() {
        val slot = CapturingSlot<JsonDict>()

        expectGetContact()
        every { ctx.body() } returns """{"text": "$text"}"""
        every { messagingManager.getContactGroup(contact) } returns group
        every { clock.currentTimeMillis() } returns timestamp
        every {
            privateMessageFactory.createPrivateMessage(
                group.id,
                timestamp,
                text
            )
        } returns privateMessage
        every { messagingManager.addLocalMessage(privateMessage) } just runs
        every { ctx.json(capture(slot)) } returns ctx

        controller.write(ctx)

        assertEquals(privateMessage.output(contact.id, text), slot.captured)
    }

    @Test
    fun writeInvalidContactId() {
        testInvalidContactId { controller.write(ctx) }
    }

    @Test
    fun writeNonexistentContactId() {
        testNonexistentContactId { controller.write(ctx) }
    }

    @Test
    fun writeNonexistentText() {
        expectGetContact()
        every { ctx.body() } returns """{"foo": "bar"}"""

        assertThrows(BadRequestResponse::class.java) { controller.write(ctx) }
    }

    @Test
    fun writeEmptyText() {
        expectGetContact()
        every { ctx.body() } returns """{"text": ""}"""

        assertThrows(BadRequestResponse::class.java) { controller.write(ctx) }
    }

    @Test
    fun writeTooLongText() {
        expectGetContact()
        every { ctx.body() } returns """{"text": "${getRandomString(MAX_PRIVATE_MESSAGE_TEXT_LENGTH + 1)}"}"""

        assertThrows(BadRequestResponse::class.java) { controller.write(ctx) }
    }

    @Test
    fun privateMessageEvent() {
        val event = PrivateMessageReceivedEvent(header, contact.id)

        every { messagingManager.getMessageText(message.id) } returns text
        every { webSocketController.sendEvent(EVENT_PRIVATE_MESSAGE, event.output(text)) } just runs

        controller.eventOccurred(event)
    }

    @Test
    fun testOutputPrivateMessageHeader() {
        val json = """
            {
                "text": "$text",
                "type": "PrivateMessage",
                "timestamp": $timestamp,
                "groupId": ${toJson(header.groupId.bytes)},
                "contactId": ${contact.id.int},
                "local": ${header.isLocal},
                "seen": ${header.isSeen},
                "read": ${header.isRead},
                "sent": ${header.isSent},
                "id": ${toJson(header.id.bytes)}
            }
        """
        assertJsonEquals(json, header.output(contact.id, text))
    }

    @Test
    fun testOutputPrivateMessage() {
        val json = """
            {
                "text": "$text",
                "type": "PrivateMessage",
                "timestamp": ${message.timestamp},
                "groupId": ${toJson(message.groupId.bytes)},
                "contactId": ${contact.id.int},
                "local": true,
                "seen": false,
                "read": true,
                "sent": false,
                "id": ${toJson(message.id.bytes)}
            }
        """
        assertJsonEquals(json, privateMessage.output(contact.id, text))
    }

    @Test
    fun testIntroductionRequestWithNullText() {
        val request = IntroductionRequest(
            message.id, group.id, timestamp, true, true, false, true, sessionId, author, null,
            false, false
        )
        val json = """
            {
                "text": null,
                "type": "IntroductionRequest",
                "timestamp": $timestamp,
                "groupId": ${toJson(request.groupId.bytes)},
                "contactId": ${contact.id.int},
                "local": ${request.isLocal},
                "seen": ${request.isSeen},
                "read": ${request.isRead},
                "sent": ${request.isSent},
                "id": ${toJson(request.id.bytes)},
                "sessionId": ${toJson(request.sessionId.bytes)},
                "name": ${request.name},
                "answered": ${request.wasAnswered()},
                "alreadyContact": ${request.isContact}
            }
        """
        assertJsonEquals(json, request.output(contact.id))
    }

    private fun expectGetContact() {
        every { ctx.pathParam("contactId") } returns contact.id.int.toString()
        every { contactManager.getContact(contact.id) } returns contact
    }

    private fun testNonexistentContactId(function: () -> Context) {
        every { ctx.pathParam("contactId") } returns "42"
        every { contactManager.getContact(ContactId(42)) } throws NoSuchContactException()

        assertThrows(NotFoundResponse::class.java) { function.invoke() }
    }

    private fun testInvalidContactId(function: () -> Context) {
        every { ctx.pathParam("contactId") } returns "foo"

        assertThrows(NotFoundResponse::class.java) { function.invoke() }
    }

}
