package org.briarproject.briar.api.telegram;

import org.briarproject.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface TelegramAuthSession {

	TelegramAuthState getCurrentState();

	void start();

	void submitIdentifier(String identifier);

	void submitCode(String code);

	void submitPassword(String password);

	void close();
}
