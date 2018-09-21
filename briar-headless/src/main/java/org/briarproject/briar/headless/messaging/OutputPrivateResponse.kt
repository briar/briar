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

internal fun PrivateResponse.output(contactId: ContactId): Map<String, Any> {
    val map: HashMap<String, Any> = hashMapOf(
        "sessionId" to sessionId.bytes,
        "accepted" to wasAccepted()
    )
    map.putAll((this as PrivateMessageHeader).output(contactId, null))
    return map
}

internal fun IntroductionResponse.output(contactId: ContactId): Map<String, Any> {
    val map: HashMap<String, Any> = hashMapOf(
        "type" to "org.briarproject.briar.api.introduction.IntroductionResponse",
        "introducedAuthor" to introducedAuthor.output(),
        "introducer" to isIntroducer
    )
    map.putAll((this as PrivateResponse).output(contactId))
    return map
}

internal fun InvitationResponse.output(contactId: ContactId): Map<String, Any> {
    val map: HashMap<String, Any> = hashMapOf("shareableId" to shareableId.bytes)
    map.putAll((this as PrivateResponse).output(contactId))
    return map
}

internal fun BlogInvitationResponse.output(contactId: ContactId): Map<String, Any> {
    val map: HashMap<String, Any> = hashMapOf(
        "type" to "org.briarproject.briar.api.blog.BlogInvitationResponse"
    )
    map.putAll((this as InvitationResponse).output(contactId))
    return map
}

internal fun ForumInvitationResponse.output(contactId: ContactId): Map<String, Any> {
    val map: HashMap<String, Any> = hashMapOf(
        "type" to "org.briarproject.briar.api.blog.BlogInvitationResponse"
    )
    map.putAll((this as InvitationResponse).output(contactId))
    return map
}

internal fun GroupInvitationResponse.output(contactId: ContactId): Map<String, Any> {
    val map: HashMap<String, Any> = hashMapOf(
        "type" to "org.briarproject.briar.api.privategroup.invitation.GroupInvitationResponse"
    )
    map.putAll((this as InvitationResponse).output(contactId))
    return map
}