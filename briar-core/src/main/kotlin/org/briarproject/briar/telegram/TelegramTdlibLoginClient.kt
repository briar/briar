package org.briarproject.briar.telegram

import org.briarproject.briar.api.telegram.TelegramAuthSession.RecoverableErrorDetail
import org.briarproject.briar.api.telegram.TelegramAuthState

interface TelegramTdlibLoginClient {
	fun start(): TelegramAuthState
	fun getRecoverableErrorDetail(): RecoverableErrorDetail
	fun submitIdentifier(identifier: String): TelegramAuthState
	fun submitCode(code: String): TelegramAuthState
	fun submitPassword(password: String): TelegramAuthState
	fun close(): TelegramAuthState
}
