package org.briarproject.briar.telegram;

import org.briarproject.briar.api.telegram.TelegramAuthState;
import org.briarproject.nullsafety.NotNullByDefault;

@NotNullByDefault
interface TelegramTdlibLoginClient {

	TelegramAuthState start();

	TelegramAuthState submitIdentifier(String identifier);

	TelegramAuthState submitCode(String code);

	TelegramAuthState submitPassword(String password);

	TelegramAuthState close();
}
