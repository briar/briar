package org.briarproject.briar.telegram

import org.briarproject.briar.api.telegram.TelegramConnector

class StubTelegramConnector : TelegramConnector {
	override fun isEnabled(): Boolean = true
}
