package org.briarproject.briar.headless.messaging

import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.briar.api.blog.BlogInvitationRequest
import org.briarproject.briar.api.conversation.ConversationMessageHeader
import org.briarproject.briar.api.conversation.ConversationRequest
import org.briarproject.briar.api.forum.ForumInvitationRequest
import org.briarproject.briar.api.introduction.IntroductionRequest
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationRequest
import org.briarproject.briar.api.sharing.InvitationRequest
import org.briarproject.briar.headless.json.JsonDict

internal fun ConversationRequest<*>.output(contactId: ContactId): JsonDict {
    val dict = (this as ConversationMessageHeader).output(contactId, text)
    dict.putAll(
        "sessionId" to sessionId.bytes,
        "name" to name,
        "answered" to wasAnswered()
    )
    return dict
}

internal fun IntroductionRequest.output(contactId: ContactId): JsonDict {
    val dict = (this as ConversationRequest<*>).output(contactId)
    dict.putAll(
        "type" to "IntroductionRequest",
        "alreadyContact" to isContact
    )
    return dict
}

internal fun InvitationRequest<*>.output(contactId: ContactId): JsonDict {
    val dict = (this as ConversationRequest<*>).output(contactId)
    dict["canBeOpened"] = canBeOpened()
    return dict
}

internal fun BlogInvitationRequest.output(contactId: ContactId): JsonDict {
    val dict = (this as InvitationRequest<*>).output(contactId)
    dict["type"] = "BlogInvitationRequest"
    return dict
}

internal fun ForumInvitationRequest.output(contactId: ContactId): JsonDict {
    val dict = (this as InvitationRequest<*>).output(contactId)
    dict["type"] = "ForumInvitationRequest"
    return dict
}

internal fun GroupInvitationRequest.output(contactId: ContactId): JsonDict {
    val dict = (this as InvitationRequest<*>).output(contactId)
    dict["type"] = "GroupInvitationRequest"
    return dict
}
