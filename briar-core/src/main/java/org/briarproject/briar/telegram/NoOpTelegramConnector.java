package org.briarproject.briar.telegram;

import org.briarproject.briar.api.telegram.TelegramConnector;
import org.briarproject.nullsafety.NotNullByDefault;

@NotNullByDefault
class NoOpTelegramConnector implements TelegramConnector {

	@Override
	public boolean isEnabled() {
		return false;
	}
}
