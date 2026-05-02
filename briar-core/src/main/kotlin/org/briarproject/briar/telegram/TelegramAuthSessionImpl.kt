package org.briarproject.briar.telegram

import org.briarproject.briar.api.telegram.TelegramAuthSession
import org.briarproject.briar.api.telegram.TelegramAuthSession.RecoverableErrorDetail
import org.briarproject.briar.api.telegram.TelegramAuthState
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class TelegramAuthSessionImpl(
	private val tdlibLoginClient: TelegramTdlibLoginClient,
) : TelegramAuthSession {

	private var currentState = TelegramAuthState.CLOSED

	override fun getCurrentState(): TelegramAuthState = currentState

	override fun getRecoverableErrorDetail(): RecoverableErrorDetail =
		tdlibLoginClient.getRecoverableErrorDetail()

	override fun start() {
		currentState = tdlibLoginClient.start()
	}

	override fun submitIdentifier(identifier: String) {
		currentState = tdlibLoginClient.submitIdentifier(identifier)
	}

	override fun submitCode(code: String) {
		currentState = tdlibLoginClient.submitCode(code)
	}

	override fun submitPassword(password: String) {
		currentState = tdlibLoginClient.submitPassword(password)
	}

	override fun close() {
		currentState = tdlibLoginClient.close()
	}
}

class NoOpTelegramTdlibLoginClient : TelegramTdlibLoginClient {
	override fun start(): TelegramAuthState = TelegramAuthState.CLOSED

	override fun getRecoverableErrorDetail(): RecoverableErrorDetail =
		RecoverableErrorDetail.NONE

	override fun submitIdentifier(identifier: String): TelegramAuthState =
		TelegramAuthState.CLOSED

	override fun submitCode(code: String): TelegramAuthState =
		TelegramAuthState.CLOSED

	override fun submitPassword(password: String): TelegramAuthState =
		TelegramAuthState.CLOSED

	override fun close(): TelegramAuthState = TelegramAuthState.CLOSED
}

class StubTelegramTdlibLoginClient @JvmOverloads constructor(
	private val tdlibDirectory: File = File("harbor-telegram"),
) : TelegramTdlibLoginClient {

	private companion object {
		private const val AUTHORIZATION_UPDATE_TIMEOUT_MS = 1_000L
	}

	private class PendingAuthorizationUpdate {
		val authorizationStateClassName = AtomicReference("")
		val updateReceived = CountDownLatch(1)
	}

	private var lastAuthorizationStateClassName = ""
	@Volatile
	private var activeClientGeneration = 0L
	private var nextClientGeneration = 0L
	@Volatile
	private var pendingAuthorizationUpdate: PendingAuthorizationUpdate? = null
	private var recoverableErrorDetail = RecoverableErrorDetail.NONE
	private var tdlibClient: Any? = null

	override fun start(): TelegramAuthState {
		closeTdlibClient()
		if (!tdlibClientClassExists()) {
			return recoverableError(RecoverableErrorDetail.MISSING_TDLIB)
		}
		return mapAuthorizationStateClassName(awaitAuthorizationStateClassName(true))
	}

	override fun getRecoverableErrorDetail(): RecoverableErrorDetail =
		recoverableErrorDetail

	override fun submitIdentifier(identifier: String): TelegramAuthState {
		if (!hasText(identifier) || tdlibClient == null) {
			return recoverableError(RecoverableErrorDetail.NONE)
		}
		try {
			if (lastAuthorizationStateClassName == "AuthorizationStateWaitTdlibParameters") {
				val tdlibParametersUpdate = prepareAuthorizationUpdate()
				send(createSetTdlibParametersRequest())
				if (awaitPreparedAuthorizationStateClassName(tdlibParametersUpdate) !=
						"AuthorizationStateWaitPhoneNumber") {
					return recoverableError(RecoverableErrorDetail.NONE)
				}
			}
			if (lastAuthorizationStateClassName != "AuthorizationStateWaitPhoneNumber") {
				return recoverableError(RecoverableErrorDetail.NONE)
			}
			val phoneNumberUpdate = prepareAuthorizationUpdate()
			if (sendReturnsError(createSetAuthenticationPhoneNumberRequest(identifier))) {
				return recoverableError(RecoverableErrorDetail.INVALID_IDENTIFIER)
			}
			return mapAuthorizationStateClassName(
					awaitPreparedAuthorizationStateClassName(phoneNumberUpdate),
			)
		} catch (e: ReflectiveOperationException) {
			closeTdlibClient()
			return recoverableError(RecoverableErrorDetail.NONE)
		} catch (e: LinkageError) {
			closeTdlibClient()
			return recoverableError(RecoverableErrorDetail.NONE)
		} catch (e: InterruptedException) {
			Thread.currentThread().interrupt()
			closeTdlibClient()
			return recoverableError(RecoverableErrorDetail.NONE)
		}
	}

	override fun submitCode(code: String): TelegramAuthState {
		if (!hasText(code) || tdlibClient == null ||
				lastAuthorizationStateClassName != "AuthorizationStateWaitCode") {
			return recoverableError(RecoverableErrorDetail.NONE)
		}
		try {
			val codeUpdate = prepareAuthorizationUpdate()
			if (sendReturnsError(createCheckAuthenticationCodeRequest(code))) {
				return recoverableError(RecoverableErrorDetail.INVALID_CODE)
			}
			return mapAuthorizationStateClassName(awaitPreparedAuthorizationStateClassName(codeUpdate))
		} catch (e: ReflectiveOperationException) {
			closeTdlibClient()
			return recoverableError(RecoverableErrorDetail.NONE)
		} catch (e: LinkageError) {
			closeTdlibClient()
			return recoverableError(RecoverableErrorDetail.NONE)
		} catch (e: InterruptedException) {
			Thread.currentThread().interrupt()
			closeTdlibClient()
			return recoverableError(RecoverableErrorDetail.NONE)
		}
	}

	override fun submitPassword(password: String): TelegramAuthState {
		if (!hasText(password) || tdlibClient == null ||
				lastAuthorizationStateClassName != "AuthorizationStateWaitPassword") {
			return recoverableError(RecoverableErrorDetail.NONE)
		}
		try {
			val passwordUpdate = prepareAuthorizationUpdate()
			if (sendReturnsError(createCheckAuthenticationPasswordRequest(password))) {
				return recoverableError(RecoverableErrorDetail.INVALID_PASSWORD)
			}
			return mapAuthorizationStateClassName(
					awaitPreparedAuthorizationStateClassName(passwordUpdate),
			)
		} catch (e: ReflectiveOperationException) {
			closeTdlibClient()
			return recoverableError(RecoverableErrorDetail.NONE)
		} catch (e: LinkageError) {
			closeTdlibClient()
			return recoverableError(RecoverableErrorDetail.NONE)
		} catch (e: InterruptedException) {
			Thread.currentThread().interrupt()
			closeTdlibClient()
			return recoverableError(RecoverableErrorDetail.NONE)
		}
	}

	override fun close(): TelegramAuthState {
		closeTdlibClient()
		return clearRecoverableErrorDetail(TelegramAuthState.CLOSED)
	}

	private fun awaitAuthorizationStateClassName(createClient: Boolean): String {
		val pendingAuthorizationUpdate = prepareAuthorizationUpdate()
		return try {
			if (createClient) tdlibClient = createTdlibClient()
			awaitPreparedAuthorizationStateClassName(pendingAuthorizationUpdate)
		} catch (e: ReflectiveOperationException) {
			closeTdlibClient()
			""
		} catch (e: LinkageError) {
			closeTdlibClient()
			""
		} catch (e: InterruptedException) {
			Thread.currentThread().interrupt()
			closeTdlibClient()
			""
		}
	}

	private fun prepareAuthorizationUpdate(): PendingAuthorizationUpdate {
		return PendingAuthorizationUpdate().also {
			pendingAuthorizationUpdate = it
		}
	}

	@Throws(InterruptedException::class)
	private fun awaitPreparedAuthorizationStateClassName(
		pendingAuthorizationUpdate: PendingAuthorizationUpdate,
	): String {
		if (tdlibClient == null || !pendingAuthorizationUpdate.updateReceived.await(
					AUTHORIZATION_UPDATE_TIMEOUT_MS,
					TimeUnit.MILLISECONDS,
			)) {
			closeTdlibClient()
			return ""
		}
		return pendingAuthorizationUpdate.authorizationStateClassName.get().also {
			lastAuthorizationStateClassName = it
		}
	}

	@Throws(ReflectiveOperationException::class)
	private fun createTdlibClient(): Any {
		val clientGeneration = ++nextClientGeneration
		activeClientGeneration = clientGeneration
		val clientClass = Class.forName("org.drinkless.tdlib.Client")
		val resultHandlerClass = Class.forName("org.drinkless.tdlib.Client\$ResultHandler")
		val exceptionHandlerClass = Class.forName("org.drinkless.tdlib.Client\$ExceptionHandler")
		val updateHandler = Proxy.newProxyInstance(
				resultHandlerClass.classLoader,
				arrayOf(resultHandlerClass),
			) { _, method, args ->
			val pendingAuthorizationUpdate = pendingAuthorizationUpdate
			if (clientGeneration == activeClientGeneration &&
					method.name == "onResult" && args != null && args.size == 1 &&
					pendingAuthorizationUpdate != null) {
				val className = getAuthorizationStateClassName(args[0])
				if (className.isNotEmpty() &&
						pendingAuthorizationUpdate.authorizationStateClassName.compareAndSet("", className)) {
					pendingAuthorizationUpdate.updateReceived.countDown()
				}
			}
			null
		}
		val create = clientClass.getMethod(
				"create",
				resultHandlerClass,
				exceptionHandlerClass,
				exceptionHandlerClass,
		)
		return create.invoke(null, updateHandler, null, null)
	}

	@Throws(ReflectiveOperationException::class)
	private fun createSetTdlibParametersRequest(): Any {
		val databaseDirectory = File(tdlibDirectory, "database")
		val filesDirectory = File(tdlibDirectory, "files")
		databaseDirectory.mkdirs()
		filesDirectory.mkdirs()
		val request = Class.forName("org.drinkless.tdlib.TdApi\$SetTdlibParameters")
				.getConstructor()
				.newInstance()
		setFieldIfPresent(request, "useTestDc", false)
		setFieldIfPresent(request, "databaseDirectory", databaseDirectory.path)
		setFieldIfPresent(request, "filesDirectory", filesDirectory.path)
		setFieldIfPresent(request, "databaseEncryptionKey", ByteArray(0))
		setFieldIfPresent(request, "useFileDatabase", true)
		setFieldIfPresent(request, "useChatInfoDatabase", true)
		setFieldIfPresent(request, "useMessageDatabase", true)
		setFieldIfPresent(request, "useSecretChats", true)
		setFieldIfPresent(request, "apiId", 94575)
		setFieldIfPresent(request, "apiHash", "a3406de8d171bb422bb6ddf3bbd800e2")
		setFieldIfPresent(request, "systemLanguageCode", "en")
		setFieldIfPresent(request, "deviceModel", "Harbor Android")
		setFieldIfPresent(request, "systemVersion", "Android")
		setFieldIfPresent(request, "applicationVersion", "Harbor")
		return request
	}

	@Throws(ReflectiveOperationException::class)
	private fun createSetAuthenticationPhoneNumberRequest(identifier: String): Any {
		val settingsClass = Class.forName(
				"org.drinkless.tdlib.TdApi\$PhoneNumberAuthenticationSettings",
		)
		val settings = settingsClass.getConstructor().newInstance()
		return Class.forName("org.drinkless.tdlib.TdApi\$SetAuthenticationPhoneNumber")
				.getConstructor(String::class.java, settingsClass)
				.newInstance(identifier, settings)
	}

	@Throws(ReflectiveOperationException::class)
	private fun createCheckAuthenticationCodeRequest(code: String): Any {
		return Class.forName("org.drinkless.tdlib.TdApi\$CheckAuthenticationCode")
				.getConstructor(String::class.java)
				.newInstance(code)
	}

	@Throws(ReflectiveOperationException::class)
	private fun createCheckAuthenticationPasswordRequest(password: String): Any {
		return Class.forName("org.drinkless.tdlib.TdApi\$CheckAuthenticationPassword")
				.getConstructor(String::class.java)
				.newInstance(password)
	}

	@Throws(ReflectiveOperationException::class)
	private fun setFieldIfPresent(target: Any, name: String, value: Any) {
		try {
			target.javaClass.getField(name).set(target, value)
		} catch (_: NoSuchFieldException) {
		}
	}

	@Throws(ReflectiveOperationException::class)
	private fun send(request: Any) {
		val functionClass = Class.forName("org.drinkless.tdlib.TdApi\$Function")
		val resultHandlerClass = Class.forName("org.drinkless.tdlib.Client\$ResultHandler")
		tdlibClient!!.javaClass.getMethod("send", functionClass, resultHandlerClass)
				.invoke(tdlibClient, request, null)
	}

	@Throws(ReflectiveOperationException::class, InterruptedException::class)
	private fun sendReturnsError(request: Any): Boolean {
		val resultClassName = AtomicReference("")
		val resultReceived = CountDownLatch(1)
		val functionClass = Class.forName("org.drinkless.tdlib.TdApi\$Function")
		val resultHandlerClass = Class.forName("org.drinkless.tdlib.Client\$ResultHandler")
		val resultHandler = Proxy.newProxyInstance(
				resultHandlerClass.classLoader,
				arrayOf(resultHandlerClass),
			) { _, method, args ->
			if (method.name == "onResult" && args != null && args.size == 1 &&
					resultClassName.compareAndSet("", args[0]?.javaClass?.simpleName ?: "")) {
				resultReceived.countDown()
			}
			null
		}
		tdlibClient!!.javaClass.getMethod("send", functionClass, resultHandlerClass)
				.invoke(tdlibClient, request, resultHandler)
		if (!resultReceived.await(AUTHORIZATION_UPDATE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
			closeTdlibClient()
		}
		return resultClassName.get() == "Error"
	}

	@Throws(ReflectiveOperationException::class)
	private fun getAuthorizationStateClassName(update: Any?): String {
		if (update?.javaClass?.simpleName != "UpdateAuthorizationState") {
			return ""
		}
		val authorizationState = update.javaClass.getField("authorizationState").get(update)
		return authorizationState?.javaClass?.simpleName ?: ""
	}

	private fun mapAuthorizationStateClassName(className: String): TelegramAuthState {
		return when (className) {
			"AuthorizationStateWaitTdlibParameters",
			"AuthorizationStateWaitPhoneNumber" -> clearRecoverableErrorDetail(TelegramAuthState.IDENTIFIER_ENTRY)
			"AuthorizationStateWaitCode" -> clearRecoverableErrorDetail(TelegramAuthState.CODE_ENTRY)
			"AuthorizationStateWaitPassword" -> clearRecoverableErrorDetail(TelegramAuthState.PASSWORD_ENTRY)
			"AuthorizationStateReady" -> clearRecoverableErrorDetail(TelegramAuthState.READY)
			"AuthorizationStateClosed" -> clearRecoverableErrorDetail(TelegramAuthState.CLOSED)
			else -> {
				closeTdlibClient()
				recoverableError(RecoverableErrorDetail.NONE)
			}
		}
	}

	private fun clearRecoverableErrorDetail(state: TelegramAuthState): TelegramAuthState {
		recoverableErrorDetail = RecoverableErrorDetail.NONE
		return state
	}

	private fun recoverableError(detail: RecoverableErrorDetail): TelegramAuthState {
		recoverableErrorDetail = detail
		return TelegramAuthState.RECOVERABLE_ERROR
	}

	private fun closeTdlibClient() {
		lastAuthorizationStateClassName = ""
		completePendingAuthorizationUpdate("AuthorizationStateClosed")
		val client = tdlibClient ?: return
		tdlibClient = null
		try {
			val functionClass = Class.forName("org.drinkless.tdlib.TdApi\$Function")
			val resultHandlerClass = Class.forName("org.drinkless.tdlib.Client\$ResultHandler")
			val send: Method = client.javaClass.getMethod("send", functionClass, resultHandlerClass)
			val closeRequest = Class.forName("org.drinkless.tdlib.TdApi\$Close")
					.getConstructor()
					.newInstance()
			send.invoke(client, closeRequest, null)
		} catch (_: ReflectiveOperationException) {
		} catch (_: LinkageError) {
		}
	}

	private fun completePendingAuthorizationUpdate(className: String) {
		pendingAuthorizationUpdate?.let {
			if (it.authorizationStateClassName.compareAndSet("", className)) {
				it.updateReceived.countDown()
			}
		}
	}

	private fun hasText(value: String): Boolean = value.trim().isNotEmpty()

	private fun tdlibClientClassExists(): Boolean {
		return try {
			Class.forName(
					"org.drinkless.tdlib.Client",
					false,
					StubTelegramTdlibLoginClient::class.java.classLoader,
			)
			true
		} catch (_: ClassNotFoundException) {
			false
		} catch (_: LinkageError) {
			false
		}
	}
}
