package org.briarproject.briar.headless.contact

import org.briarproject.bramble.api.contact.Contact
import org.briarproject.bramble.api.contact.event.ContactAddedEvent
import org.briarproject.bramble.identity.output
import org.briarproject.briar.headless.json.JsonDict

internal fun Contact.output() = JsonDict(
    "contactId" to id.int,
    "author" to author.output(),
    "verified" to isVerified
).apply {
    alias?.let { put("alias", it) }
    handshakePublicKey?.let { put("handshakePublicKey", it.encoded) }
}

internal fun ContactAddedEvent.output() = JsonDict(
    "contactId" to contactId.int,
    "verified" to isVerified
)
