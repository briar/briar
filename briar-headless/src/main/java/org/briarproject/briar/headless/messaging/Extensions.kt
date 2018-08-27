package org.briarproject.briar.headless.messaging

import org.briarproject.bramble.api.contact.ContactId
import org.briarproject.briar.api.introduction.IntroductionRequest
import org.briarproject.briar.api.introduction.IntroductionResponse
import org.briarproject.briar.api.messaging.PrivateMessage
import org.briarproject.briar.api.messaging.PrivateMessageHeader
import org.briarproject.briar.api.messaging.PrivateRequest
import org.briarproject.briar.api.messaging.PrivateResponse
import org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent
import org.briarproject.briar.api.sharing.InvitationRequest
import org.briarproject.briar.api.sharing.InvitationResponse

internal fun PrivateMessageHeader.output(
    contactId: ContactId,
    body: String?
) = OutputPrivateMessageHeader(this, contactId, body)

internal fun PrivateMessage.output(contactId: ContactId, body: String) =
    OutputPrivateMessageHeader(this, contactId, body)

internal fun PrivateMessageReceivedEvent<*>.output(body: String) =
    messageHeader.output(contactId, body)

internal fun IntroductionRequest.output(contactId: ContactId) =
    OutputIntroductionRequest(this, contactId)

internal fun PrivateRequest<*>.output(contactId: ContactId): OutputPrivateMessage {
    return when (this) {
        is IntroductionRequest -> OutputIntroductionRequest(this, contactId)
        is InvitationRequest -> OutputInvitationRequest(this, contactId)
        else -> throw AssertionError("Unknown PrivateRequest")
    }
}

internal fun PrivateResponse.output(contactId: ContactId): OutputPrivateMessage {
    return when (this) {
        is IntroductionResponse -> OutputIntroductionResponse(this, contactId)
        is InvitationResponse -> OutputInvitationResponse(this, contactId)
        else -> throw AssertionError("Unknown PrivateResponse")
    }
}
