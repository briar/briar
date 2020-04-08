package org.briarproject.briar.headless.contact

import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import org.briarproject.bramble.api.contact.ContactManager
import org.briarproject.bramble.api.contact.HandshakeLinkConstants.LINK_REGEX
import org.briarproject.bramble.api.contact.PendingContactId
import org.briarproject.bramble.api.contact.event.ContactAddedEvent
import org.briarproject.bramble.api.contact.event.PendingContactAddedEvent
import org.briarproject.bramble.api.contact.event.PendingContactRemovedEvent
import org.briarproject.bramble.api.contact.event.PendingContactStateChangedEvent
import org.briarproject.bramble.api.db.NoSuchContactException
import org.briarproject.bramble.api.db.NoSuchPendingContactException
import org.briarproject.bramble.api.event.Event
import org.briarproject.bramble.api.event.EventListener
import org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH
import org.briarproject.bramble.util.StringUtils.toUtf8
import org.briarproject.briar.api.conversation.ConversationManager
import org.briarproject.briar.headless.event.WebSocketController
import org.briarproject.briar.headless.getContactIdFromPathParam
import org.briarproject.briar.headless.getFromJson
import org.briarproject.briar.headless.json.JsonDict
import org.spongycastle.util.encoders.Base64
import org.spongycastle.util.encoders.DecoderException
import javax.annotation.concurrent.Immutable
import javax.inject.Inject
import javax.inject.Singleton

internal const val EVENT_CONTACT_ADDED = "ContactAddedEvent"
internal const val EVENT_PENDING_CONTACT_STATE_CHANGED = "PendingContactStateChangedEvent"
internal const val EVENT_PENDING_CONTACT_ADDED = "PendingContactAddedEvent"
internal const val EVENT_PENDING_CONTACT_REMOVED = "PendingContactRemovedEvent"

@Immutable
@Singleton
internal class ContactControllerImpl
@Inject
constructor(
    private val contactManager: ContactManager,
    private val conversationManager: ConversationManager,
    private val objectMapper: ObjectMapper,
    private val webSocket: WebSocketController
) : ContactController, EventListener {

    override fun eventOccurred(e: Event) = when (e) {
        is ContactAddedEvent -> {
            webSocket.sendEvent(EVENT_CONTACT_ADDED, e.output())
        }
        is PendingContactStateChangedEvent -> {
            webSocket.sendEvent(EVENT_PENDING_CONTACT_STATE_CHANGED, e.output())
        }
        is PendingContactAddedEvent -> {
            webSocket.sendEvent(EVENT_PENDING_CONTACT_ADDED, e.output())
        }
        is PendingContactRemovedEvent -> {
            webSocket.sendEvent(EVENT_PENDING_CONTACT_REMOVED, e.output())
        }
        else -> {
        }
    }

    override fun list(ctx: Context): Context {
        val contacts = contactManager.contacts.map { contact ->
            contact.output(conversationManager)
        }
        return ctx.json(contacts)
    }

    override fun getLink(ctx: Context): Context {
        val linkDict = JsonDict("link" to contactManager.handshakeLink)
        return ctx.json(linkDict)
    }

    override fun addPendingContact(ctx: Context): Context {
        val link = ctx.getFromJson(objectMapper, "link")
        val alias = ctx.getFromJson(objectMapper, "alias")
        if (!LINK_REGEX.matcher(link).find()) throw BadRequestResponse("Invalid Link")
        val aliasUtf8 = toUtf8(alias)
        if (aliasUtf8.isEmpty() || aliasUtf8.size > MAX_AUTHOR_NAME_LENGTH)
            throw BadRequestResponse("Invalid Alias")
        val pendingContact = contactManager.addPendingContact(link, alias)
        return ctx.json(pendingContact.output())
    }

    override fun listPendingContacts(ctx: Context): Context {
        val pendingContacts = contactManager.pendingContacts.map { pair ->
            JsonDict("pendingContact" to pair.first.output(), "state" to pair.second.output())
        }
        return ctx.json(pendingContacts)
    }

    override fun removePendingContact(ctx: Context): Context {
        // construct and check PendingContactId
        val pendingContactString = ctx.getFromJson(objectMapper, "pendingContactId")
        val pendingContactBytes = try {
            Base64.decode(pendingContactString)
        } catch (e: DecoderException) {
            throw NotFoundResponse()
        }
        if (pendingContactBytes.size != PendingContactId.LENGTH) throw NotFoundResponse()
        val id = PendingContactId(pendingContactBytes)
        // remove
        try {
            contactManager.removePendingContact(id)
        } catch (e: NoSuchPendingContactException) {
            throw NotFoundResponse()
        }
        return ctx
    }

    override fun delete(ctx: Context): Context {
        val contactId = ctx.getContactIdFromPathParam()
        try {
            contactManager.removeContact(contactId)
        } catch (e: NoSuchContactException) {
            throw NotFoundResponse()
        }
        return ctx
    }

}
