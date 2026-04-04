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
	private Object tdlibClient;
	@Override
	public TelegramAuthState start() {
		closeTdlibClient();
		if (StubTelegramTdlibLoginClient.class.getResource("/org/drinkless/tdlib/Client.class") == null) {
			return TelegramAuthState.RECOVERABLE_ERROR;
		}
		return mapAuthorizationStateClassName(awaitAuthorizationStateClassName());
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
		closeTdlibClient();
		return TelegramAuthState.CLOSED;
	}
	private String awaitAuthorizationStateClassName() {
		CountDownLatch updateReceived = new CountDownLatch(1);
		AtomicReference<String> authorizationStateClassName =
				new AtomicReference<>("");
		try {
			tdlibClient = createTdlibClient(updateReceived,
					authorizationStateClassName);
			if (tdlibClient == null || !updateReceived.await(
					AUTHORIZATION_UPDATE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
				closeTdlibClient();
				return "";
			}
			return authorizationStateClassName.get();
		} catch (ReflectiveOperationException | LinkageError e) {
			closeTdlibClient();
			return "";
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			closeTdlibClient();
			return "";
		}
	}
	private Object createTdlibClient(CountDownLatch updateReceived,
			AtomicReference<String> authorizationStateClassName)
			throws ReflectiveOperationException {
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
