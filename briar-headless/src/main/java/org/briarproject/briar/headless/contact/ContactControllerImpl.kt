package org.briarproject.briar.headless.contact

import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.Context
import io.javalin.NotFoundResponse
import org.briarproject.bramble.api.contact.ContactManager
import org.briarproject.bramble.api.contact.PendingContactId
import org.briarproject.bramble.api.contact.event.ContactAddedRemotelyEvent
import org.briarproject.bramble.api.contact.event.PendingContactRemovedEvent
import org.briarproject.bramble.api.contact.event.PendingContactStateChangedEvent
import org.briarproject.bramble.api.db.NoSuchContactException
import org.briarproject.bramble.api.db.NoSuchPendingContactException
import org.briarproject.bramble.api.event.Event
import org.briarproject.bramble.api.event.EventListener
import org.briarproject.briar.headless.event.WebSocketController
import org.briarproject.briar.headless.getContactIdFromPathParam
import org.briarproject.briar.headless.getFromJson
import org.briarproject.briar.headless.json.JsonDict
import org.spongycastle.util.encoders.Base64
import org.spongycastle.util.encoders.DecoderException
import javax.annotation.concurrent.Immutable
import javax.inject.Inject
import javax.inject.Singleton

internal const val EVENT_CONTACT_ADDED_REMOTELY = "ContactAddedRemotelyEvent"
internal const val EVENT_PENDING_CONTACT_STATE_CHANGED = "PendingContactStateChangedEvent"
internal const val EVENT_PENDING_CONTACT_REMOVED = "PendingContactRemovedEvent"

@Immutable
@Singleton
internal class ContactControllerImpl
@Inject
constructor(
    private val contactManager: ContactManager,
    private val objectMapper: ObjectMapper,
    private val webSocket: WebSocketController
) : ContactController, EventListener {

    override fun eventOccurred(e: Event) = when (e) {
        is ContactAddedRemotelyEvent -> {
            webSocket.sendEvent(EVENT_CONTACT_ADDED_REMOTELY, e.output())
        }
        is PendingContactStateChangedEvent -> {
            webSocket.sendEvent(EVENT_PENDING_CONTACT_STATE_CHANGED, e.output())
        }
        is PendingContactRemovedEvent -> {
            webSocket.sendEvent(EVENT_PENDING_CONTACT_REMOVED, e.output())
        }
        else -> {
        }
    }

    override fun list(ctx: Context): Context {
        val contacts = contactManager.contacts.map { contact ->
            contact.output()
        }
        return ctx.json(contacts)
    }

    override fun link(ctx: Context): Context {
        val linkDict = JsonDict("link" to contactManager.handshakeLink)
        return ctx.json(linkDict)
    }

    override fun addPendingContact(ctx: Context): Context {
        val link = ctx.getFromJson(objectMapper, "link")
        val alias = ctx.getFromJson(objectMapper, "alias")
        val pendingContact = contactManager.addPendingContact(link, alias)
        return ctx.json(pendingContact.output())
    }

    override fun listPendingContacts(ctx: Context): Context {
        val pendingContacts = contactManager.pendingContacts.map { pendingContact ->
            pendingContact.output()
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
