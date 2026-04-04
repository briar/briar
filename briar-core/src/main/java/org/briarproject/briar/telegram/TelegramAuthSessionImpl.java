package org.briarproject.briar.telegram;

import org.briarproject.briar.api.telegram.TelegramAuthSession;
import org.briarproject.briar.api.telegram.TelegramAuthState;
import org.briarproject.nullsafety.NotNullByDefault;

@NotNullByDefault
class TelegramAuthSessionImpl implements TelegramAuthSession {

	private final TelegramTdlibLoginClient tdlibLoginClient;
	private TelegramAuthState currentState = TelegramAuthState.CLOSED;

	TelegramAuthSessionImpl(TelegramTdlibLoginClient tdlibLoginClient) {
		this.tdlibLoginClient = tdlibLoginClient;
	}

	@Override
	public TelegramAuthState getCurrentState() {
		return currentState;
	}

	@Override
	public void start() {
		currentState = tdlibLoginClient.start();
	}

	@Override
	public void submitIdentifier(String identifier) {
		currentState = tdlibLoginClient.submitIdentifier(identifier);
	}

	@Override
	public void submitCode(String code) {
		currentState = tdlibLoginClient.submitCode(code);
	}

	@Override
	public void submitPassword(String password) {
		currentState = tdlibLoginClient.submitPassword(password);
	}

	@Override
	public void close() {
		currentState = tdlibLoginClient.close();
	}
}

@NotNullByDefault
class NoOpTelegramTdlibLoginClient implements TelegramTdlibLoginClient {

	@Override
	public TelegramAuthState start() {
		return TelegramAuthState.CLOSED;
	}

	@Override
	public TelegramAuthState submitIdentifier(String identifier) {
		return TelegramAuthState.CLOSED;
	}

	@Override
	public TelegramAuthState submitCode(String code) {
		return TelegramAuthState.CLOSED;
	}

	@Override
	public TelegramAuthState submitPassword(String password) {
		return TelegramAuthState.CLOSED;
	}

	@Override
	public TelegramAuthState close() {
		return TelegramAuthState.CLOSED;
	}
}

@NotNullByDefault
class StubTelegramTdlibLoginClient implements TelegramTdlibLoginClient {

	@Override
	public TelegramAuthState start() {
		return StubTelegramTdlibLoginClient.class.getResource("/org/drinkless/tdlib/Client.class") != null ? TelegramAuthState.IDENTIFIER_ENTRY : TelegramAuthState.RECOVERABLE_ERROR;
	}

	@Override
	public TelegramAuthState submitIdentifier(String identifier) {
		return hasText(identifier)
				? TelegramAuthState.CODE_ENTRY
				: TelegramAuthState.RECOVERABLE_ERROR;
	}

	@Override
	public TelegramAuthState submitCode(String code) {
		return hasText(code)
				? TelegramAuthState.PASSWORD_ENTRY
				: TelegramAuthState.RECOVERABLE_ERROR;
	}

	@Override
	public TelegramAuthState submitPassword(String password) {
		return hasText(password)
				? TelegramAuthState.READY
				: TelegramAuthState.RECOVERABLE_ERROR;
	}

	@Override
	public TelegramAuthState close() {
		return TelegramAuthState.CLOSED;
	}

	private boolean hasText(String value) {
		return !value.trim().isEmpty();
	}
}
