package org.briarproject.briar.headless.messaging

import io.javalin.BadRequestResponse
import io.javalin.Context
import io.javalin.NotFoundResponse
import org.briarproject.bramble.api.contact.Contact
import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.bramble.api.contact.ContactManager
import org.briarproject.bramble.api.db.DatabaseExecutor
import org.briarproject.bramble.api.db.NoSuchContactException
import org.briarproject.bramble.api.event.Event
import org.briarproject.bramble.api.event.EventListener
import org.briarproject.bramble.api.system.Clock
import org.briarproject.bramble.util.StringUtils.utf8IsTooLong
import org.briarproject.briar.api.messaging.*
import org.briarproject.briar.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_BODY_LENGTH
import org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent
import org.briarproject.briar.headless.event.WebSocketController
import org.briarproject.briar.headless.event.output
import java.util.concurrent.Executor
import javax.annotation.concurrent.Immutable
import javax.inject.Inject
import javax.inject.Singleton

internal const val EVENT_PRIVATE_MESSAGE =
    "org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent"

@Immutable
@Singleton
internal class MessagingControllerImpl
@Inject constructor(
    private val messagingManager: MessagingManager,
    private val conversationManager: ConversationManager,
    private val privateMessageFactory: PrivateMessageFactory,
    private val contactManager: ContactManager,
    private val webSocketController: WebSocketController,
    @DatabaseExecutor private val dbExecutor: Executor,
    private val clock: Clock
) : MessagingController, EventListener {

    override fun list(ctx: Context): Context {
        val contact = getContact(ctx)
        val messages = conversationManager.getMessageHeaders(contact.id).map { header ->
            when (header) {
                is PrivateRequest<*> -> header.output(contact.id)
                is PrivateResponse -> header.output(contact.id)
                else -> {
                    val body = messagingManager.getMessageBody(header.id)
                    header.output(contact.id, body)
                }
            }
        }.sortedBy { it.timestamp }
        return ctx.json(messages)
    }

    override fun write(ctx: Context): Context {
        val contact = getContact(ctx)

        val message = ctx.formParam("text")
        if (message == null || message.isEmpty())
            throw BadRequestResponse("Expecting Message text")
        if (utf8IsTooLong(message, MAX_PRIVATE_MESSAGE_BODY_LENGTH))
            throw BadRequestResponse("Message text too large")

        val group = messagingManager.getContactGroup(contact)
        val now = clock.currentTimeMillis()
        val m = privateMessageFactory.createPrivateMessage(group.id, now, message)

        messagingManager.addLocalMessage(m)
        return ctx.json(m.output(contact.id, message))
    }

    override fun eventOccurred(e: Event) {
        when (e) {
            is PrivateMessageReceivedEvent<*> -> dbExecutor.execute {
                val body = messagingManager.getMessageBody(e.messageHeader.id)
                webSocketController.sendEvent(EVENT_PRIVATE_MESSAGE, e.output(body))
            }
        }
    }

    private fun getContact(ctx: Context): Contact {
        val contactString = ctx.pathParam("contactId")
        val contactInt = try {
            Integer.parseInt(contactString)
        } catch (e: NumberFormatException) {
            throw NotFoundResponse()
        }
        val contactId = ContactId(contactInt)
        return try {
            contactManager.getContact(contactId)
        } catch (e: NoSuchContactException) {
            throw NotFoundResponse()
        }
    }

}
