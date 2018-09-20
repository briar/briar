package org.briarproject.briar.headless.messaging

import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.briar.api.messaging.PrivateMessage
import org.briarproject.briar.api.messaging.PrivateMessageHeader
import javax.annotation.concurrent.Immutable

@Immutable
internal abstract class OutputPrivateMessage(
    protected open val iHeader: PrivateMessageHeader,
    protected open val iContactId: ContactId,
    open val body: String?
) {

    open val type: String get() = throw NotImplementedError()
    val contactId: Int get() = iContactId.int
    val timestamp: Long get() = iHeader.timestamp
    val read: Boolean get() = iHeader.isRead
    val seen: Boolean get() = iHeader.isSeen
    val sent: Boolean get() = iHeader.isSent
    val local: Boolean get() = iHeader.isLocal
    val id: ByteArray get() = iHeader.id.bytes
    val groupId: ByteArray get() = iHeader.groupId.bytes

}

@Immutable
internal data class OutputPrivateMessageHeader(
    override val iHeader: PrivateMessageHeader,
    override val iContactId: ContactId,
    override val body: String?
) : OutputPrivateMessage(iHeader, iContactId, body) {

    override val type = "org.briarproject.briar.api.messaging.PrivateMessageHeader"

    /**
     * Only meant for own [PrivateMessage]s directly after creation.
     */
    internal constructor(m: PrivateMessage, contactId: ContactId, body: String) : this(
        PrivateMessageHeader(
            m.message.id,
            m.message.groupId,
            m.message.timestamp,
            true,
            true,
            true,
            true
        ), contactId, body
    )
}

internal fun PrivateMessageHeader.output(
    contactId: ContactId,
    body: String?
) = OutputPrivateMessageHeader(this, contactId, body)

internal fun PrivateMessage.output(contactId: ContactId, body: String) =
    OutputPrivateMessageHeader(this, contactId, body)
