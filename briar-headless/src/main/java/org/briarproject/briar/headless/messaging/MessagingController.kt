package org.briarproject.briar.headless.messaging

import io.javalin.BadRequestResponse
import io.javalin.Context
import io.javalin.NotFoundResponse
import org.briarproject.bramble.api.contact.Contact
import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.bramble.api.contact.ContactManager
import org.briarproject.bramble.api.db.NoSuchContactException
import org.briarproject.bramble.api.system.Clock
import org.briarproject.briar.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_BODY_LENGTH
import org.briarproject.briar.api.messaging.MessagingManager
import org.briarproject.briar.api.messaging.PrivateMessageFactory
import javax.annotation.concurrent.Immutable
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
@Singleton
class MessagingController @Inject
constructor(
    private val messagingManager: MessagingManager,
    private val privateMessageFactory: PrivateMessageFactory,
    private val contactManager: ContactManager,
    private val clock: Clock
) {

    fun list(ctx: Context): Context {
        val contact = getContact(ctx)

        val messages = messagingManager.getMessageHeaders(contact.id).map { header ->
            val body = messagingManager.getMessageBody(header.id)
            header.output(body)
        }
        return ctx.json(messages)
    }

    fun write(ctx: Context): Context {
        val contact = getContact(ctx)

        val message = ctx.formParam("message")
        if (message == null || message.isEmpty())
            throw BadRequestResponse("Expecting Message text")
        if (message.length > MAX_PRIVATE_MESSAGE_BODY_LENGTH)
            throw BadRequestResponse("Message text too large")

        val group = messagingManager.getContactGroup(contact)
        val now = clock.currentTimeMillis()
        val m = privateMessageFactory.createPrivateMessage(group.id, now, message)

        messagingManager.addLocalMessage(m)
        return ctx.json(m.output(message))
    }

    private fun getContact(ctx: Context): Contact {
        val contactString = ctx.pathParam("contactId")
        val contactInt = Integer.parseInt(contactString)
        val contactId = ContactId(contactInt)
        return try {
            contactManager.getContact(contactId)
        } catch (e: NoSuchContactException) {
            throw NotFoundResponse()
        }
    }

}
