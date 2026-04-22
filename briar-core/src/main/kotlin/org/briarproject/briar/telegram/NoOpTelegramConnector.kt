package org.briarproject.briar.telegram

import org.briarproject.briar.api.telegram.TelegramConnector

class NoOpTelegramConnector : TelegramConnector {
	override fun isEnabled(): Boolean = false
}
