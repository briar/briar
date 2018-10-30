package org.briarproject.briar.headless.event

import org.briarproject.briar.api.conversation.event.ConversationMessageReceivedEvent
import org.briarproject.briar.headless.messaging.output
import javax.annotation.concurrent.Immutable

@Immutable
internal class OutputEvent(val name: String, val data: Any) {
    val type = "event"
}

internal fun ConversationMessageReceivedEvent<*>.output(text: String) =
    messageHeader.output(contactId, text)
