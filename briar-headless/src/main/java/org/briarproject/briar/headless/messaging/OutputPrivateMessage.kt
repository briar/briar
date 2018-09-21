package org.briarproject.briar.headless.messaging

import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.briar.api.messaging.PrivateMessage
import org.briarproject.briar.api.messaging.PrivateMessageHeader

internal fun PrivateMessageHeader.output(contactId: ContactId, body: String?): Map<String, Any> {
    val map: HashMap<String, Any> = hashMapOf(
        "type" to "org.briarproject.briar.api.messaging.PrivateMessageHeader",
        "contactId" to contactId.int,
        "timestamp" to timestamp,
        "read" to isRead,
        "seen" to isSeen,
        "sent" to isSent,
        "local" to isLocal,
        "id" to id.bytes,
        "groupId" to groupId.bytes
    )
    if (body != null) map.put("body", body)
    return map
}

internal fun PrivateMessage.output(contactId: ContactId, body: String) = mapOf(
    "type" to "org.briarproject.briar.api.messaging.PrivateMessageHeader",
    "contactId" to contactId.int,
    "timestamp" to message.timestamp,
    "read" to true,
    "seen" to true,
    "sent" to true,
    "local" to true,
    "id" to message.id.bytes,
    "groupId" to message.groupId.bytes,
    "body" to body
)