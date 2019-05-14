package org.briarproject.briar.headless.contact

import org.briarproject.bramble.api.contact.PendingContact
import org.briarproject.bramble.api.contact.PendingContactState.*
import org.briarproject.briar.headless.json.JsonDict

internal fun PendingContact.output() = JsonDict(
    "pendingContactId" to id.bytes,
    "alias" to alias,
    "state" to when(state) {
        WAITING_FOR_CONNECTION -> "waiting_for_connection"
        CONNECTED -> "connected"
        ADDING_CONTACT -> "adding_contact"
        FAILED -> "failed"
        else -> throw AssertionError()
    },
    "timestamp" to timestamp
)