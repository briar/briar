package org.briarproject.briar.headless.messaging

import io.javalin.BadRequestResponse
import io.javalin.Context
import io.javalin.NotFoundResponse
import io.javalin.json.JavalinJson.toJson
import io.mockk.*
import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.bramble.api.db.NoSuchContactException
import org.briarproject.bramble.test.ImmediateExecutor
import org.briarproject.bramble.util.StringUtils.getRandomString
import org.briarproject.briar.api.messaging.*
import org.briarproject.briar.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_BODY_LENGTH
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

    private val header = PrivateMessageHeader(message.id, group.id, timestamp, true, true, true, true)
    private val headers = listOf(header)

    @Test
    fun list() {
        expectGetContact()
        every { conversationManager.getMessageHeaders(contact.id) } returns headers
        every { messagingManager.getMessageBody(message.id) } returns body
        every { ctx.json(listOf(header.output(contact.id, body))) } returns ctx

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
    fun listPrivateMessage() {
        val privateMessage = PrivateMessage(message)
        val slot = CapturingSlot<JsonDict>()

        expectGetContact()
        every { ctx.formParam("text") } returns body
        every { messagingManager.getContactGroup(contact) } returns group
        every { clock.currentTimeMillis() } returns timestamp
        every {
            privateMessageFactory.createPrivateMessage(
                group.id,
                timestamp,
                body
            )
        } returns privateMessage
        every { messagingManager.addLocalMessage(privateMessage) } just runs
        every { ctx.json(capture(slot)) } returns ctx

        controller.write(ctx)

        val output = slot.captured
        assertEquals(contact.id.int, output.get("contactId"))
        assertEquals(body, output.get("body"))
        assertEquals(message.id.bytes, output.get("id"))
        assertEquals("org.briarproject.briar.api.messaging.PrivateMessageHeader",
            output.get("type"))
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
    fun writeNonexistentBody() {
        expectGetContact()
        every { ctx.formParam("text") } returns null

        assertThrows(BadRequestResponse::class.java) { controller.write(ctx) }
    }

    @Test
    fun writeEmptyBody() {
        expectGetContact()
        every { ctx.formParam("text") } returns ""

        assertThrows(BadRequestResponse::class.java) { controller.write(ctx) }
    }

    @Test
    fun writeTooLongBody() {
        expectGetContact()
        every { ctx.formParam("text") } returns getRandomString(MAX_PRIVATE_MESSAGE_BODY_LENGTH + 1)

        assertThrows(BadRequestResponse::class.java) { controller.write(ctx) }
    }

    @Test
    fun privateMessageEvent() {
        val event = PrivateMessageReceivedEvent(header, contact.id)

        every { messagingManager.getMessageBody(message.id) } returns body
        every { webSocketController.sendEvent(EVENT_PRIVATE_MESSAGE, event.output(body)) } just runs

        controller.eventOccurred(event)
    }

    @Test
    fun testOutputPrivateMessageHeader() {
        val json = """
            {
                "body": "$body",
                "type": "org.briarproject.briar.api.messaging.PrivateMessageHeader",
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
        assertJsonEquals(json, header.output(contact.id, body))
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
