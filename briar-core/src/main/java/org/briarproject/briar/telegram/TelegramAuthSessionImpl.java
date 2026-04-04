package org.briarproject.briar.telegram;
import org.briarproject.briar.api.telegram.TelegramAuthSession;
import org.briarproject.briar.api.telegram.TelegramAuthState;
import org.briarproject.nullsafety.NotNullByDefault;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
	public TelegramAuthState start() { return TelegramAuthState.CLOSED; }
	@Override
	public TelegramAuthState submitIdentifier(String identifier) { return TelegramAuthState.CLOSED; }
	@Override
	public TelegramAuthState submitCode(String code) { return TelegramAuthState.CLOSED; }
	@Override
	public TelegramAuthState submitPassword(String password) { return TelegramAuthState.CLOSED; }
	@Override
	public TelegramAuthState close() { return TelegramAuthState.CLOSED; }
}
@NotNullByDefault
class StubTelegramTdlibLoginClient implements TelegramTdlibLoginClient {
	private static final long AUTHORIZATION_UPDATE_TIMEOUT_MS = 250L;
	private final AtomicReference<String> authorizationStateClassName =
			new AtomicReference<>("");
	private CountDownLatch updateReceived = new CountDownLatch(0);
	private String lastAuthorizationStateClassName = "";
	private Object tdlibClient;
	@Override
	public TelegramAuthState start() {
		closeTdlibClient();
		if (StubTelegramTdlibLoginClient.class.getResource("/org/drinkless/tdlib/Client.class") == null) {
			return TelegramAuthState.RECOVERABLE_ERROR;
		}
		return mapAuthorizationStateClassName(awaitAuthorizationStateClassName(true));
	}
	@Override
	public TelegramAuthState submitIdentifier(String identifier) {
		if (!hasText(identifier) || tdlibClient == null) {
			return TelegramAuthState.RECOVERABLE_ERROR;
		}
		try {
			if ("AuthorizationStateWaitTdlibParameters".equals(
					lastAuthorizationStateClassName)) {
				prepareAuthorizationUpdate();
				send(createSetTdlibParametersRequest());
				if (!"AuthorizationStateWaitPhoneNumber".equals(
						awaitPreparedAuthorizationStateClassName())) {
					return TelegramAuthState.RECOVERABLE_ERROR;
				}
			}
			if (!"AuthorizationStateWaitPhoneNumber".equals(
					lastAuthorizationStateClassName)) {
				return TelegramAuthState.RECOVERABLE_ERROR;
			}
			prepareAuthorizationUpdate();
			send(createSetAuthenticationPhoneNumberRequest(identifier));
			return mapAuthorizationStateClassName(
					awaitPreparedAuthorizationStateClassName());
		} catch (ReflectiveOperationException | LinkageError e) {
			closeTdlibClient();
			return TelegramAuthState.RECOVERABLE_ERROR;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			closeTdlibClient();
			return TelegramAuthState.RECOVERABLE_ERROR;
		}
	}
	@Override
	public TelegramAuthState submitCode(String code) {
		if (!hasText(code) || tdlibClient == null
				|| !"AuthorizationStateWaitCode".equals(lastAuthorizationStateClassName)) {
			return TelegramAuthState.RECOVERABLE_ERROR;
		}
		try {
			prepareAuthorizationUpdate();
			send(createCheckAuthenticationCodeRequest(code));
			return mapAuthorizationStateClassName(awaitPreparedAuthorizationStateClassName());
		} catch (ReflectiveOperationException | LinkageError e) {
			closeTdlibClient();
			return TelegramAuthState.RECOVERABLE_ERROR;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			closeTdlibClient();
			return TelegramAuthState.RECOVERABLE_ERROR;
		}
	}
	@Override
	public TelegramAuthState submitPassword(String password) {
		return hasText(password)
				? TelegramAuthState.READY
				: TelegramAuthState.RECOVERABLE_ERROR;
	}
	@Override
	public TelegramAuthState close() {
		closeTdlibClient();
		return TelegramAuthState.CLOSED;
	}
	private String awaitAuthorizationStateClassName(boolean createClient) {
		prepareAuthorizationUpdate();
		try {
			if (createClient) tdlibClient = createTdlibClient();
			return awaitPreparedAuthorizationStateClassName();
		} catch (ReflectiveOperationException | LinkageError e) {
			closeTdlibClient();
			return "";
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			closeTdlibClient();
			return "";
		}
	}
	private void prepareAuthorizationUpdate() {
		authorizationStateClassName.set("");
		updateReceived = new CountDownLatch(1);
	}
	private String awaitPreparedAuthorizationStateClassName()
			throws InterruptedException {
		if (tdlibClient == null || !updateReceived.await(
				AUTHORIZATION_UPDATE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
			closeTdlibClient();
			return "";
		}
		return lastAuthorizationStateClassName = authorizationStateClassName.get();
	}
	private Object createTdlibClient() throws ReflectiveOperationException {
		Class<?> clientClass = Class.forName("org.drinkless.tdlib.Client");
		Class<?> resultHandlerClass =
				Class.forName("org.drinkless.tdlib.Client$ResultHandler");
		Class<?> exceptionHandlerClass =
				Class.forName("org.drinkless.tdlib.Client$ExceptionHandler");
		Object updateHandler = Proxy.newProxyInstance(
				resultHandlerClass.getClassLoader(),
				new Class<?>[] {resultHandlerClass},
				(proxy, method, args) -> {
					if ("onResult".equals(method.getName()) && args != null
							&& args.length == 1) {
						String className =
								getAuthorizationStateClassName(args[0]);
						if (!className.isEmpty()
								&& authorizationStateClassName.compareAndSet("",
								className)) {
							updateReceived.countDown();
						}
					}
					return null;
				});
		Method create = clientClass.getMethod("create", resultHandlerClass,
				exceptionHandlerClass, exceptionHandlerClass);
		return create.invoke(null, updateHandler, null, null);
	}
	private Object createSetTdlibParametersRequest()
			throws ReflectiveOperationException {
		Object request = Class.forName(
				"org.drinkless.tdlib.TdApi$SetTdlibParameters")
				.getConstructor().newInstance();
		setFieldIfPresent(request, "useTestDc", false);
		setFieldIfPresent(request, "databaseDirectory", "harbor-telegram");
		setFieldIfPresent(request, "filesDirectory", "harbor-telegram");
		setFieldIfPresent(request, "databaseEncryptionKey", new byte[0]);
		setFieldIfPresent(request, "useFileDatabase", true);
		setFieldIfPresent(request, "useChatInfoDatabase", true);
		setFieldIfPresent(request, "useMessageDatabase", true);
		setFieldIfPresent(request, "useSecretChats", true);
		setFieldIfPresent(request, "apiId", 94575);
		setFieldIfPresent(request, "apiHash", "a3406de8d171bb422bb6ddf3bbd800e2");
		setFieldIfPresent(request, "systemLanguageCode", "en");
		setFieldIfPresent(request, "deviceModel", "Harbor Android");
		setFieldIfPresent(request, "systemVersion", "Android");
		setFieldIfPresent(request, "applicationVersion", "Harbor");
		return request;
	}
	private Object createSetAuthenticationPhoneNumberRequest(String identifier)
			throws ReflectiveOperationException {
		Class<?> settingsClass = Class.forName(
				"org.drinkless.tdlib.TdApi$PhoneNumberAuthenticationSettings");
		return Class.forName(
				"org.drinkless.tdlib.TdApi$SetAuthenticationPhoneNumber")
				.getConstructor(String.class, settingsClass)
				.newInstance(identifier, null);
	}
	private Object createCheckAuthenticationCodeRequest(String code) throws ReflectiveOperationException {
		return Class.forName("org.drinkless.tdlib.TdApi$CheckAuthenticationCode")
				.getConstructor(String.class).newInstance(code);
	}
	private void setFieldIfPresent(Object target, String name, Object value)
			throws ReflectiveOperationException {
		try {
			target.getClass().getField(name).set(target, value);
		} catch (NoSuchFieldException e) {
		}
	}
	private void send(Object request) throws ReflectiveOperationException {
		Class<?> functionClass =
				Class.forName("org.drinkless.tdlib.TdApi$Function");
		Class<?> resultHandlerClass =
				Class.forName("org.drinkless.tdlib.Client$ResultHandler");
		tdlibClient.getClass().getMethod("send", functionClass,
				resultHandlerClass).invoke(tdlibClient, request, null);
	}
	private String getAuthorizationStateClassName(Object update)
			throws ReflectiveOperationException {
		if (!"UpdateAuthorizationState".equals(
				update.getClass().getSimpleName())) {
			return "";
		}
		Object authorizationState =
				update.getClass().getField("authorizationState").get(update);
		return authorizationState == null
				? ""
				: authorizationState.getClass().getSimpleName();
	}
	private TelegramAuthState mapAuthorizationStateClassName(String className) {
		switch (className) {
			case "AuthorizationStateWaitTdlibParameters":
			case "AuthorizationStateWaitPhoneNumber":
				return TelegramAuthState.IDENTIFIER_ENTRY;
			case "AuthorizationStateWaitCode":
				return TelegramAuthState.CODE_ENTRY;
			case "AuthorizationStateWaitPassword":
				return TelegramAuthState.PASSWORD_ENTRY;
			case "AuthorizationStateReady":
				return TelegramAuthState.READY;
			case "AuthorizationStateClosed":
				return TelegramAuthState.CLOSED;
			default:
				closeTdlibClient();
				return TelegramAuthState.RECOVERABLE_ERROR;
		}
	}
	private void closeTdlibClient() {
		if (tdlibClient == null) return;
		Object client = tdlibClient;
		tdlibClient = null;
		try {
			Class<?> functionClass =
					Class.forName("org.drinkless.tdlib.TdApi$Function");
			Class<?> resultHandlerClass =
					Class.forName("org.drinkless.tdlib.Client$ResultHandler");
			Method send = client.getClass().getMethod("send", functionClass,
					resultHandlerClass);
			Object closeRequest = Class.forName("org.drinkless.tdlib.TdApi$Close")
					.getConstructor().newInstance();
			send.invoke(client, closeRequest, null);
		} catch (ReflectiveOperationException | LinkageError e) {
		}
	}
	private boolean hasText(String value) {
		return !value.trim().isEmpty();
	}
}
