package org.briarproject.briar.api.telegram;

import org.briarproject.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface TelegramAuthSession {

	enum RecoverableErrorDetail {
		NONE,
		MISSING_TDLIB,
		INVALID_IDENTIFIER
	}

	TelegramAuthState getCurrentState();

	RecoverableErrorDetail getRecoverableErrorDetail();

	void start();

	void submitIdentifier(String identifier);

	void submitCode(String code);

	void submitPassword(String password);

	void close();
}
