package org.briarproject.briar.telegram;

import org.briarproject.briar.api.telegram.TelegramAuthSession;
import org.briarproject.briar.api.telegram.TelegramAuthState;
import org.briarproject.nullsafety.NotNullByDefault;

@NotNullByDefault
class NoOpTelegramAuthSession implements TelegramAuthSession {

	@Override
	public TelegramAuthState getCurrentState() {
		return TelegramAuthState.CLOSED;
	}

	@Override
	public void start() {
	}

	@Override
	public void submitIdentifier(String identifier) {
	}

	@Override
	public void submitCode(String code) {
	}

	@Override
	public void submitPassword(String password) {
	}

	@Override
	public void close() {
	}
}
