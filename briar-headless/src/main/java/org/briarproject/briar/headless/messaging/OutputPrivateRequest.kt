@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package org.briarproject.briar.headless.messaging

import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.briar.api.blog.BlogInvitationRequest
import org.briarproject.briar.api.forum.ForumInvitationRequest
import org.briarproject.briar.api.introduction.IntroductionRequest
import org.briarproject.briar.api.messaging.PrivateRequest
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationRequest
import org.briarproject.briar.api.sharing.InvitationRequest
import javax.annotation.concurrent.Immutable

@Immutable
internal abstract class OutputPrivateRequest(header: PrivateRequest<*>, contactId: ContactId) :
    OutputPrivateMessage(header, contactId, header.message) {

    val sessionId: ByteArray = header.sessionId.bytes
    val name: String = header.name
    val answered = header.wasAnswered()
}

@Immutable
internal class OutputIntroductionRequest(header: IntroductionRequest, contactId: ContactId) :
    OutputPrivateRequest(header, contactId) {

    override val type = "org.briarproject.briar.api.introduction.IntroductionRequest"
    val alreadyContact = header.isContact
}

@Immutable
internal class OutputInvitationRequest(header: InvitationRequest<*>, contactId: ContactId) :
    OutputPrivateRequest(header, contactId) {

    override val type = when (header) {
        is ForumInvitationRequest -> "org.briarproject.briar.api.forum.ForumInvitationRequest"
        is BlogInvitationRequest -> "org.briarproject.briar.api.blog.BlogInvitationRequest"
        is GroupInvitationRequest -> "org.briarproject.briar.api.privategroup.invitation.GroupInvitationRequest"
        else -> throw AssertionError("Unknown InvitationRequest")
    }
    val canBeOpened = header.canBeOpened()
}
