package org.briarproject.briar.headless.contact

import org.briarproject.bramble.api.contact.PendingContact
import org.briarproject.bramble.api.contact.PendingContactState
import org.briarproject.bramble.api.contact.PendingContactState.*
import org.briarproject.bramble.api.contact.event.PendingContactRemovedEvent
import org.briarproject.bramble.api.contact.event.PendingContactStateChangedEvent
import org.briarproject.briar.headless.json.JsonDict

internal fun PendingContact.output() = JsonDict(
    "pendingContactId" to id.bytes,
    "alias" to alias,
    "state" to state.output(),
    "timestamp" to timestamp
)

internal fun PendingContactState.output() = when(this) {
    WAITING_FOR_CONNECTION -> "waiting_for_connection"
    CONNECTED -> "connected"
    ADDING_CONTACT -> "adding_contact"
    FAILED -> "failed"
    else -> throw AssertionError()
}

internal fun PendingContactStateChangedEvent.output() = JsonDict(
    "pendingContactId" to id.bytes,
    "state" to pendingContactState.output()
)

internal fun PendingContactRemovedEvent.output() = JsonDict(
    "pendingContactId" to id.bytes
)
