package org.briarproject.briar.headless.messaging

import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.bramble.identity.output
import org.briarproject.briar.api.blog.BlogInvitationResponse
import org.briarproject.briar.api.conversation.ConversationMessageHeader
import org.briarproject.briar.api.conversation.ConversationResponse
import org.briarproject.briar.api.forum.ForumInvitationResponse
import org.briarproject.briar.api.introduction.IntroductionResponse
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationResponse
import org.briarproject.briar.api.sharing.InvitationResponse
import org.briarproject.briar.headless.json.JsonDict

internal fun ConversationResponse.output(contactId: ContactId): JsonDict {
    val dict = (this as ConversationMessageHeader).output(contactId)
    dict.putAll(
        "sessionId" to sessionId.bytes,
        "accepted" to wasAccepted()
    )
    return dict
}

internal fun IntroductionResponse.output(contactId: ContactId): JsonDict {
    val dict = (this as ConversationResponse).output(contactId)
    dict.putAll(
        "type" to "IntroductionResponse",
        "introducedAuthor" to introducedAuthor.output(),
        "introducer" to isIntroducer
    )
    return dict
}

internal fun InvitationResponse.output(contactId: ContactId): JsonDict {
    val dict = (this as ConversationResponse).output(contactId)
    dict["shareableId"] = shareableId.bytes
    return dict
}

internal fun BlogInvitationResponse.output(contactId: ContactId): JsonDict {
    val dict = (this as InvitationResponse).output(contactId)
    dict["type"] = "BlogInvitationResponse"
    return dict
}

internal fun ForumInvitationResponse.output(contactId: ContactId): JsonDict {
    val dict = (this as InvitationResponse).output(contactId)
    dict["type"] = "ForumInvitationResponse"
    return dict
}

internal fun GroupInvitationResponse.output(contactId: ContactId): JsonDict {
    val dict = (this as InvitationResponse).output(contactId)
    dict["type"] = "GroupInvitationResponse"
    return dict
}
