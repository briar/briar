package org.briarproject.briar.headless.messaging

import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.briar.api.messaging.PrivateMessage
import org.briarproject.briar.api.messaging.PrivateMessageHeader
import org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent

internal fun PrivateMessageHeader.output(contactId: ContactId, body: String) =
    OutputPrivateMessage(this, contactId, body)

internal fun PrivateMessage.output(contactId: ContactId, body: String) =
    OutputPrivateMessage(this, contactId, body)

internal fun PrivateMessageReceivedEvent.output(body: String) =
    messageHeader.output(contactId, body)
