@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package org.briarproject.briar.headless.messaging

import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.bramble.identity.output
import org.briarproject.briar.api.blog.BlogInvitationResponse
import org.briarproject.briar.api.forum.ForumInvitationResponse
import org.briarproject.briar.api.introduction.IntroductionResponse
import org.briarproject.briar.api.messaging.PrivateMessageHeader
import org.briarproject.briar.api.messaging.PrivateResponse
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationResponse
import org.briarproject.briar.api.sharing.InvitationResponse
import org.briarproject.briar.headless.json.JsonDict

internal fun PrivateResponse.output(contactId: ContactId): JsonDict {
    val dict = (this as PrivateMessageHeader).output(contactId, null)
    dict.putAll(
        "sessionId" to sessionId.bytes,
        "accepted" to wasAccepted()
    )
    return dict
}

internal fun IntroductionResponse.output(contactId: ContactId): JsonDict {
    val dict = (this as PrivateResponse).output(contactId)
    dict.putAll(
        "type" to "org.briarproject.briar.api.introduction.IntroductionResponse",
        "introducedAuthor" to introducedAuthor.output(),
        "introducer" to isIntroducer
    )
    return dict
}

internal fun InvitationResponse.output(contactId: ContactId): JsonDict {
    val dict = (this as PrivateResponse).output(contactId)
    dict.put("shareableId", shareableId.bytes)
    return dict
}

internal fun BlogInvitationResponse.output(contactId: ContactId): JsonDict {
    val dict = (this as InvitationResponse).output(contactId)
    dict.put("type", "org.briarproject.briar.api.blog.BlogInvitationResponse")
    return dict
}

internal fun ForumInvitationResponse.output(contactId: ContactId): JsonDict {
    val dict = (this as InvitationResponse).output(contactId)
    dict.put("type", "org.briarproject.briar.api.blog.BlogInvitationResponse")
    return dict
}

internal fun GroupInvitationResponse.output(contactId: ContactId): JsonDict {
    val dict = (this as InvitationResponse).output(contactId)
    dict.put("type", "org.briarproject.briar.api.privategroup.invitation.GroupInvitationResponse")
    return dict
}