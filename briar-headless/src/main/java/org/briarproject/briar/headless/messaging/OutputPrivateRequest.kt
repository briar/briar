@file:Suppress("unused")

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
internal data class OutputIntroductionRequest(
    override val iHeader: IntroductionRequest,
    override val iContactId: ContactId
) : OutputPrivateRequest(iHeader, iContactId) {

    override val type = "org.briarproject.briar.api.introduction.IntroductionRequest"
    val alreadyContact get() = iHeader.isContact

}

@Immutable
internal data class OutputInvitationRequest(
    override val iHeader: InvitationRequest<*>,
    override val iContactId: ContactId
) : OutputPrivateRequest(iHeader, iContactId) {

    override val type = when (iHeader) {
        is ForumInvitationRequest -> "org.briarproject.briar.api.forum.ForumInvitationRequest"
        is BlogInvitationRequest -> "org.briarproject.briar.api.blog.BlogInvitationRequest"
        is GroupInvitationRequest -> "org.briarproject.briar.api.privategroup.invitation.GroupInvitationRequest"
        else -> throw AssertionError("Unknown InvitationRequest")
    }
    val canBeOpened get() = iHeader.canBeOpened()

}

internal fun PrivateRequest<*>.output(contactId: ContactId): OutputPrivateMessage {
    return when (this) {
        is IntroductionRequest -> OutputIntroductionRequest(this, contactId)
        is InvitationRequest -> OutputInvitationRequest(this, contactId)
        else -> throw AssertionError("Unknown PrivateRequest")
    }
}

internal fun IntroductionRequest.output(contactId: ContactId) =
    OutputIntroductionRequest(this, contactId)
