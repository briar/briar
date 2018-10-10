package org.briarproject.briar.headless.event

import org.briarproject.briar.api.messaging.event.PrivateMessageReceivedEvent
import org.briarproject.briar.headless.messaging.output
import javax.annotation.concurrent.Immutable

@Immutable
internal class OutputEvent(val name: String, val data: Any) {
    val type = "event"
}

internal fun PrivateMessageReceivedEvent<*>.output(text: String) =
    messageHeader.output(contactId, text)
