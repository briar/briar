@file:Suppress("unused")

package org.briarproject.briar.headless.messaging

import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.briar.api.blog.BlogInvitationRequest
import org.briarproject.briar.api.forum.ForumInvitationRequest
import org.briarproject.briar.api.introduction.IntroductionRequest
import org.briarproject.briar.api.messaging.PrivateMessageHeader
import org.briarproject.briar.api.messaging.PrivateRequest
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationRequest
import org.briarproject.briar.api.sharing.InvitationRequest

internal fun PrivateRequest<*>.output(contactId: ContactId): Map<String, Any> {
    val map: HashMap<String, Any> = hashMapOf(
        "sessionId" to sessionId.bytes,
        "name" to name,
        "answered" to wasAnswered()
    )
    map.putAll((this as PrivateMessageHeader).output(contactId, null))
    return map
}

internal fun IntroductionRequest.output(contactId: ContactId): Map<String, Any> {
    val map: HashMap<String, Any> = hashMapOf(
        "type" to "org.briarproject.briar.api.introduction.IntroductionRequest",
        "alreadyContact" to isContact
    )
    map.putAll((this as PrivateRequest<*>).output(contactId))
    return map
}

internal fun InvitationRequest<*>.output(contactId : ContactId): Map<String, Any> {
    val map: HashMap<String, Any> = hashMapOf("canBeOpened" to canBeOpened())
    map.putAll((this as PrivateRequest<*>).output(contactId))
    return map
}

internal fun BlogInvitationRequest.output(contactId : ContactId): Map<String, Any> {
    val map: HashMap<String, Any> = hashMapOf(
        "type" to "org.briarproject.briar.api.blog.BlogInvitationRequest"
    )
    map.putAll((this as InvitationRequest<*>).output(contactId))
    return map
}

internal fun ForumInvitationRequest.output(contactId: ContactId): Map<String, Any> {
    val map: HashMap<String, Any> = hashMapOf(
        "type" to "org.briarproject.briar.api.forum.ForumInvitationRequest"
    )
    map.putAll((this as InvitationRequest<*>).output(contactId))
    return map
}

internal fun GroupInvitationRequest.output(contactId : ContactId): Map<String, Any> {
    val map: HashMap<String, Any> = hashMapOf(
        "type" to "org.briarproject.briar.api.privategroup.invitation.GroupInvitationRequest"
    )
    map.putAll((this as InvitationRequest<*>).output(contactId))
    return map
}