package org.briarproject.briar.headless.contact

import org.briarproject.bramble.api.contact.Contact
import org.briarproject.bramble.api.contact.event.ContactAddedEvent
import org.briarproject.bramble.identity.output
import org.briarproject.briar.api.conversation.ConversationManager
import org.briarproject.briar.headless.json.JsonDict

internal fun Contact.output(conversationManager: ConversationManager) = JsonDict(
    "contactId" to id.int,
    "author" to author.output(),
    "verified" to isVerified,
    "lastChatActivity" to this.getLastChatActivity(conversationManager)
).apply {
    alias?.let { put("alias", it) }
    handshakePublicKey?.let { put("handshakePublicKey", it.encoded) }
}

internal fun Contact.getLastChatActivity(conversationManager: ConversationManager) =
    conversationManager.getGroupCount(this.id).latestMsgTime

internal fun ContactAddedEvent.output() = JsonDict(
    "contactId" to contactId.int,
    "verified" to isVerified
)
