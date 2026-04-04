package org.briarproject.briar.telegram;

import org.briarproject.briar.api.telegram.TelegramAuthSession;
import org.briarproject.briar.api.telegram.TelegramAuthState;
import org.briarproject.nullsafety.NotNullByDefault;

@NotNullByDefault
class StubTelegramAuthSession implements TelegramAuthSession {

	private TelegramAuthState currentState = TelegramAuthState.CLOSED;

	@Override
	public TelegramAuthState getCurrentState() {
		return currentState;
	}

	@Override
	public void start() {
		currentState = TelegramAuthState.IDENTIFIER_ENTRY;
	}

	@Override
	public void submitIdentifier(String identifier) {
		currentState = hasText(identifier)
				? TelegramAuthState.CODE_ENTRY
				: TelegramAuthState.RECOVERABLE_ERROR;
	}

	@Override
	public void submitCode(String code) {
		currentState = hasText(code)
				? TelegramAuthState.PASSWORD_ENTRY
				: TelegramAuthState.RECOVERABLE_ERROR;
	}

	@Override
	public void submitPassword(String password) {
		currentState = hasText(password)
				? TelegramAuthState.READY
				: TelegramAuthState.RECOVERABLE_ERROR;
	}

	@Override
	public void close() {
		currentState = TelegramAuthState.CLOSED;
	}

	private boolean hasText(String value) {
		return !value.trim().isEmpty();
	}
}
