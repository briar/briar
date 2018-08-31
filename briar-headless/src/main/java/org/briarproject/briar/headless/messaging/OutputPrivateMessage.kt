package org.briarproject.briar.headless.messaging

import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.briar.api.messaging.PrivateMessage
import org.briarproject.briar.api.messaging.PrivateMessageHeader
import javax.annotation.concurrent.Immutable

@Immutable
internal data class OutputPrivateMessage(
    val body: String,
    val timestamp: Long,
    val read: Boolean,
    val seen: Boolean,
    val sent: Boolean,
    val local: Boolean,
    val id: ByteArray,
    val groupId: ByteArray,
    val contactId: Int
) {
    internal constructor(header: PrivateMessageHeader, contactId: ContactId, body: String) : this(
        body = body,
        timestamp = header.timestamp,
        read = header.isRead,
        seen = header.isSeen,
        sent = header.isSent,
        local = header.isLocal,
        id = header.id.bytes,
        groupId = header.groupId.bytes,
        contactId = contactId.int
    )

    /**
     * Only meant for own [PrivateMessage]s directly after creation.
     */
    internal constructor(m: PrivateMessage, contactId: ContactId, body: String) : this(
        body = body,
        timestamp = m.message.timestamp,
        read = true,
        seen = true,
        sent = true,
        local = true,
        id = m.message.id.bytes,
        groupId = m.message.groupId.bytes,
        contactId = contactId.int
    )
}
