package org.briarproject.briar.headless.messaging

import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.briar.api.messaging.PrivateMessage
import org.briarproject.briar.api.messaging.PrivateMessageHeader
import javax.annotation.concurrent.Immutable

@Immutable
@Suppress("unused", "MemberVisibilityCanBePrivate")
internal class OutputPrivateMessage {

    val body: String
    val timestamp: Long
    val read: Boolean
    val seen: Boolean
    val sent: Boolean
    val local: Boolean
    val id: ByteArray
    val groupId: ByteArray
    val contactId: Int

    internal constructor(header: PrivateMessageHeader, contactId: ContactId, body: String) {
        this.body = body
        this.timestamp = header.timestamp
        this.read = header.isRead
        this.seen = header.isSeen
        this.sent = header.isSent
        this.local = header.isLocal
        this.id = header.id.bytes
        this.groupId = header.groupId.bytes
        this.contactId = contactId.int
    }

    /**
     * Only meant for own [PrivateMessage]s directly after creation.
     */
    internal constructor(m: PrivateMessage, contactId: ContactId, body: String) {
        this.body = body
        this.timestamp = m.message.timestamp
        this.read = true
        this.seen = true
        this.sent = false
        this.local = true
        this.id = m.message.id.bytes
        this.groupId = m.message.groupId.bytes
        this.contactId = contactId.int
    }
}
