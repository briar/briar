package org.briarproject.briar.api.telegram;
public enum TelegramAuthState {
	IDENTIFIER_ENTRY,
	CODE_ENTRY,
	PASSWORD_ENTRY,
	READY,
	CLOSED,
	RECOVERABLE_ERROR
}
