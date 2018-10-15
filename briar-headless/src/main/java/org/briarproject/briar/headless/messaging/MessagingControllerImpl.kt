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
import org.briarproject.briar.api.blog.BlogInvitationRequest
import org.briarproject.briar.api.blog.BlogInvitationResponse
import org.briarproject.briar.api.forum.ForumInvitationRequest
import org.briarproject.briar.api.forum.ForumInvitationResponse
import org.briarproject.briar.api.introduction.IntroductionRequest
import org.briarproject.briar.api.introduction.IntroductionResponse
import org.briarproject.briar.api.messaging.*
import org.briarproject.briar.api.messaging.MessagingConstants.MAX_PRIVATE_MESSAGE_TEXT_LENGTH
import org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationRequest
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationResponse
import org.briarproject.briar.headless.event.WebSocketController
import org.briarproject.briar.headless.event.output
import org.briarproject.briar.headless.getFromJson
import org.briarproject.briar.headless.json.JsonDict
import java.util.concurrent.Executor
import javax.annotation.concurrent.Immutable
import javax.inject.Inject
import javax.inject.Singleton

internal const val EVENT_PRIVATE_MESSAGE = "PrivateMessageReceivedEvent"

@Immutable
@Singleton
internal class MessagingControllerImpl
@Inject
constructor(
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
        val jsonVisitor = JsonVisitor(contact.id, messagingManager)
        val messages = conversationManager.getMessageHeaders(contact.id)
            .sortedBy { it.timestamp }
            .map { header -> header.accept(jsonVisitor) }
        return ctx.json(messages)
    }

    override fun write(ctx: Context): Context {
        val contact = getContact(ctx)

        val message = ctx.getFromJson("text")
        if (utf8IsTooLong(message, MAX_PRIVATE_MESSAGE_TEXT_LENGTH))
            throw BadRequestResponse("Message text is too long")

        val group = messagingManager.getContactGroup(contact)
        val now = clock.currentTimeMillis()
        val m = privateMessageFactory.createPrivateMessage(group.id, now, message)

        messagingManager.addLocalMessage(m)
        return ctx.json(m.output(contact.id, message))
    }

    override fun eventOccurred(e: Event) {
        when (e) {
            is PrivateMessageReceivedEvent<*> -> dbExecutor.execute {
                val text = messagingManager.getMessageText(e.messageHeader.id)
                webSocketController.sendEvent(EVENT_PRIVATE_MESSAGE, e.output(text))
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

private class JsonVisitor(
    private val contactId: ContactId,
    private val messagingManager: MessagingManager
) : PrivateMessageVisitor<JsonDict> {

    override fun visitPrivateMessageHeader(h: PrivateMessageHeader) =
        h.output(contactId, messagingManager.getMessageText(h.id))

    override fun visitBlogInvitationRequest(r: BlogInvitationRequest) = r.output(contactId)

    override fun visitBlogInvitationResponse(r: BlogInvitationResponse) = r.output(contactId)

    override fun visitForumInvitationRequest(r: ForumInvitationRequest) = r.output(contactId)

    override fun visitForumInvitationResponse(r: ForumInvitationResponse) = r.output(contactId)

    override fun visitGroupInvitationRequest(r: GroupInvitationRequest) = r.output(contactId)

    override fun visitGroupInvitationResponse(r: GroupInvitationResponse) = r.output(contactId)

    override fun visitIntroductionRequest(r: IntroductionRequest) = r.output(contactId)

    override fun visitIntroductionResponse(r: IntroductionResponse) = r.output(contactId)
}
