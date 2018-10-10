package org.briarproject.briar.headless.messaging

import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.briar.api.messaging.PrivateMessage
import org.briarproject.briar.api.messaging.PrivateMessageHeader
import org.briarproject.briar.headless.json.JsonDict

internal fun PrivateMessageHeader.output(contactId: ContactId) = JsonDict(
    "type" to "PrivateMessage",
    "contactId" to contactId.int,
    "timestamp" to timestamp,
    "read" to isRead,
    "seen" to isSeen,
    "sent" to isSent,
    "local" to isLocal,
    "id" to id.bytes,
    "groupId" to groupId.bytes
)

internal fun PrivateMessageHeader.output(contactId: ContactId, text: String?): JsonDict {
    val dict = output(contactId)
    dict["text"] = text
    return dict
}

/**
 * Use only for outgoing messages that were just sent
 */
internal fun PrivateMessage.output(contactId: ContactId, text: String) = JsonDict(
    "type" to "PrivateMessage",
    "contactId" to contactId.int,
    "timestamp" to message.timestamp,
    "read" to true,
    "seen" to false,
    "sent" to false,
    "local" to true,
    "id" to message.id.bytes,
    "groupId" to message.groupId.bytes,
    "text" to text
)
