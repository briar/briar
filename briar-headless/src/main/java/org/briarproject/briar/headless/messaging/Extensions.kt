package org.briarproject.briar.headless.messaging

import org.briarproject.briar.api.messaging.PrivateMessage
import org.briarproject.briar.api.messaging.PrivateMessageHeader

internal fun PrivateMessageHeader.output(body: String) = OutputPrivateMessage(this, body)

internal fun PrivateMessage.output(body: String) = OutputPrivateMessage(this, body)
