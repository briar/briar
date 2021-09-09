package org.briarproject.briar.headless.messaging

import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.plugin.json.JavalinJson.toJson
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.bramble.api.db.NoSuchContactException
import org.briarproject.bramble.api.sync.MessageId
import org.briarproject.bramble.api.sync.event.MessagesAckedEvent
import org.briarproject.bramble.api.sync.event.MessagesSentEvent
import org.briarproject.bramble.test.ImmediateExecutor
import org.briarproject.bramble.test.TestUtils.getRandomId
import org.briarproject.bramble.util.StringUtils.getRandomString
import org.briarproject.briar.api.autodelete.AutoDeleteConstants.NO_AUTO_DELETE_TIMER
import org.briarproject.briar.api.client.SessionId
import org.briarproject.briar.api.conversation.DeletionResult
import org.briarproject.briar.api.identity.AuthorInfo
import org.briarproject.briar.api.identity.AuthorInfo.Status.UNVERIFIED
import org.briarproject.briar.api.identity.AuthorInfo.Status.VERIFIED
import org.briarproject.briar.api.introduction.IntroductionRequest
import org.briarproject.briar.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_TEXT_LENGTH
import org.briarproject.briar.api.messaging.MessagingManager
import org.briarproject.briar.api.messaging.PrivateMessage
import org.briarproject.briar.api.messaging.PrivateMessageFactory
import org.briarproject.briar.api.messaging.PrivateMessageHeader
import org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent
import org.briarproject.briar.headless.ControllerTest
import org.briarproject.briar.headless.event.output
import org.briarproject.briar.headless.getFromJson
import org.briarproject.briar.headless.json.JsonDict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.spongycastle.util.encoders.Base64
import kotlin.random.Random

internal class MessagingControllerImplTest : ControllerTest() {

    private val messagingManager = mockk<MessagingManager>()
    private val privateMessageFactory = mockk<PrivateMessageFactory>()
    private val dbExecutor = ImmediateExecutor()

    private val controller = MessagingControllerImpl(
        messagingManager,
        conversationManager,
        privateMessageFactory,
        contactManager,
        webSocketController,
        dbExecutor,
        objectMapper,
        clock
    )

    private val header =
        PrivateMessageHeader(
            message.id,
            group.id,
            timestamp,
            true,
            true,
            true,
            true,
            true,
            emptyList(),
            NO_AUTO_DELETE_TIMER
        )
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
            message.id, group.id, timestamp, true, true, true, false, sessionId, author, text,
            false, AuthorInfo(UNVERIFIED), NO_AUTO_DELETE_TIMER
        )

        expectGetContact()
        every { conversationManager.getMessageHeaders(contact.id) } returns listOf(request)
        every { ctx.json(listOf(request.output(contact.id))) } returns ctx

        controller.list(ctx)
    }

    @Test
    fun testEmptyList() {
        every { ctx.pathParam("contactId") } returns contact.id.int.toString()
        every { contactManager.getContact(contact.id) } returns contact
        every { conversationManager.getMessageHeaders(contact.id) } returns emptyList()
        every { ctx.json(emptyList<Any>()) } returns ctx

        controller.list(ctx)
    }

    @Test
    fun listInvalidContactId() {
        testInvalidContactId { controller.list(ctx) }
    }

    @Test
    fun testMessagesAckedEvent() {
        val messageId1 = MessageId(getRandomId())
        val messageId2 = MessageId(getRandomId())
        val messageIds = listOf(messageId1, messageId2)
        val event = MessagesAckedEvent(contact.id, messageIds)

        every {
            webSocketController.sendEvent(
                EVENT_MESSAGES_ACKED,
                event.output()
            )
        } just runs

        controller.eventOccurred(event)
    }

    @Test
    fun testMessagesSentEvent() {
        val messageId1 = MessageId(getRandomId())
        val messageId2 = MessageId(getRandomId())
        val messageIds = listOf(messageId1, messageId2)
        val event = MessagesSentEvent(contact.id, messageIds, 1234)

        every {
            webSocketController.sendEvent(
                EVENT_MESSAGES_SENT,
                event.output()
            )
        } just runs

        controller.eventOccurred(event)
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
            privateMessageFactory.createLegacyPrivateMessage(
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
    fun markMessageRead() {
        mockkStatic("org.briarproject.briar.headless.RouterKt")
        mockkStatic("org.spongycastle.util.encoders.Base64")
        expectGetContact()

        val messageIdString = message.id.bytes.toString()
        every { messagingManager.getContactGroup(contact).id } returns group.id
        every { ctx.getFromJson(objectMapper, "messageId") } returns messageIdString
        every { Base64.decode(messageIdString) } returns message.id.bytes
        every { conversationManager.setReadFlag(group.id, message.id, true) } just Runs
        every { ctx.json(messageIdString) } returns ctx

        controller.markMessageRead(ctx)
    }

    @Test
    fun markMessageReadInvalidContactId() {
        testInvalidContactId { controller.markMessageRead(ctx) }
    }

    @Test
    fun markMessageReadNonexistentId() {
        testNonexistentContactId { controller.markMessageRead(ctx) }
    }

    @Test
    fun privateMessageEvent() {
        val event = PrivateMessageReceivedEvent(header, contact.id)

        every { messagingManager.getMessageText(message.id) } returns text
        every {
            webSocketController.sendEvent(
                EVENT_CONVERSATION_MESSAGE,
                event.output(text)
            )
        } just runs

        controller.eventOccurred(event)
    }

    @Test
    fun testOutputMessagesAckedEvent() {
        val messageId1 = MessageId(getRandomId())
        val messageId2 = MessageId(getRandomId())
        val messageIds = listOf(messageId1, messageId2)
        val event = MessagesAckedEvent(contact.id, messageIds)
        val json = """
            {
                "contactId": ${contact.id.int},
                "messageIds": [
                    ${toJson(messageId1.bytes)},
                    ${toJson(messageId2.bytes)}
                ]
            }
        """
        assertJsonEquals(json, event.output())
    }

    @Test
    fun testOutputMessagesSentEvent() {
        val messageId1 = MessageId(getRandomId())
        val messageId2 = MessageId(getRandomId())
        val messageIds = listOf(messageId1, messageId2)
        val event = MessagesSentEvent(contact.id, messageIds, 1234)

        val json = """
            {
                "contactId": ${contact.id.int},
                "messageIds": [
                    ${toJson(messageId1.bytes)},
                    ${toJson(messageId2.bytes)}
                ]
            }
        """
        assertJsonEquals(json, event.output())
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
            message.id, group.id, timestamp, true, true, true, false, sessionId, author, null,
            false, AuthorInfo(VERIFIED), NO_AUTO_DELETE_TIMER
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

    @Test
    fun testDeleteAllMessages() {
        val result = DeletionResult()
        every { ctx.pathParam("contactId") } returns "1"
        every { conversationManager.deleteAllMessages(ContactId(1)) } returns result
        every { ctx.json(result.output()) } returns ctx
        controller.deleteAllMessages(ctx)
    }

    @Test
    fun testDeleteAllMessagesInvalidContactId() {
        every { ctx.pathParam("contactId") } returns "foo"
        assertThrows(NotFoundResponse::class.java) {
            controller.deleteAllMessages(ctx)
        }
    }

    @Test
    fun testDeleteAllMessagesNonexistentContactId() {
        every { ctx.pathParam("contactId") } returns "1"
        every { conversationManager.deleteAllMessages(ContactId(1)) } throws NoSuchContactException()
        assertThrows(NotFoundResponse::class.java) {
            controller.deleteAllMessages(ctx)
        }
    }

    @Test
    fun testOutputDeletionResult() {
        val result = DeletionResult()
        if (Random.nextBoolean()) result.addInvitationNotAllSelected()
        if (Random.nextBoolean()) result.addInvitationSessionInProgress()
        if (Random.nextBoolean()) result.addIntroductionNotAllSelected()
        if (Random.nextBoolean()) result.addIntroductionSessionInProgress()
        val json = """
            {
                "allDeleted": ${result.allDeleted()},
                "hasIntroductionSessionInProgress": ${result.hasIntroductionSessionInProgress()},
                "hasInvitationSessionInProgress": ${result.hasInvitationSessionInProgress()},
                "hasNotAllIntroductionSelected": ${result.hasNotAllIntroductionSelected()},
                "hasNotAllInvitationSelected": ${result.hasNotAllInvitationSelected()}
            }
        """
        assertJsonEquals(json, result.output())
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
