package org.drinkless.tdlib;

import java.util.ArrayList;
import java.util.List;

public class Client {

	public interface ResultHandler {
		void onResult(Object object);
	}

	public interface ExceptionHandler {
		void onException(Throwable throwable);
	}

	private static final List<String> sentRequestNames = new ArrayList<>();
	private static final List<Long> authorizationUpdateDelaySequenceMs =
			new ArrayList<>();
	private static long authorizationUpdateDelayMs = 0L;
	private static String lastPhoneNumber = "";
	private static String lastDatabaseDirectory = "";
	private static String lastFilesDirectory = "";

	private final ResultHandler updateHandler;

	private Client(ResultHandler updateHandler) {
		this.updateHandler = updateHandler;
		emitAuthorizationState(new TdApi.AuthorizationStateWaitTdlibParameters());
	}

	public static Client create(ResultHandler updateHandler,
			ExceptionHandler updatesExceptionHandler,
			ExceptionHandler defaultExceptionHandler) {
		return new Client(updateHandler);
	}

	public void send(TdApi.Function request, ResultHandler resultHandler) {
		sentRequestNames.add(request.getClass().getSimpleName());
		if (request instanceof TdApi.SetTdlibParameters) {
			TdApi.SetTdlibParameters parameters =
					(TdApi.SetTdlibParameters) request;
			lastDatabaseDirectory = parameters.databaseDirectory;
			lastFilesDirectory = parameters.filesDirectory;
			if (resultHandler != null) resultHandler.onResult(new TdApi.Ok());
			emitAuthorizationState(new TdApi.AuthorizationStateWaitPhoneNumber());
			return;
		}
		if (request instanceof TdApi.SetAuthenticationPhoneNumber) {
			TdApi.SetAuthenticationPhoneNumber phoneNumberRequest =
					(TdApi.SetAuthenticationPhoneNumber) request;
			lastPhoneNumber = phoneNumberRequest.phoneNumber;
			if (phoneNumberRequest.settings == null) {
				if (resultHandler != null) {
					resultHandler.onResult(new TdApi.Error());
				}
				return;
			}
			if (phoneNumberRequest.phoneNumber.contains("invalid")) {
				if (resultHandler != null) {
					resultHandler.onResult(new TdApi.Error());
				}
				return;
			}
			if (resultHandler != null) resultHandler.onResult(new TdApi.Ok());
			emitAuthorizationState(new TdApi.AuthorizationStateWaitCode());
			return;
		}
		if (request instanceof TdApi.CheckAuthenticationCode) {
			TdApi.CheckAuthenticationCode codeRequest =
					(TdApi.CheckAuthenticationCode) request;
			if (codeRequest.code.contains("invalid")) {
				if (resultHandler != null) {
					resultHandler.onResult(new TdApi.Error());
				}
				return;
			}
			if (resultHandler != null) resultHandler.onResult(new TdApi.Ok());
			if (codeRequest.code.contains("password-required")) {
				emitAuthorizationState(
						new TdApi.AuthorizationStateWaitPassword());
				return;
			}
			emitAuthorizationState(new TdApi.AuthorizationStateReady());
			return;
		}
		if (request instanceof TdApi.CheckAuthenticationPassword) {
			TdApi.CheckAuthenticationPassword passwordRequest =
					(TdApi.CheckAuthenticationPassword) request;
			if (passwordRequest.password.contains("invalid")) {
				if (resultHandler != null) {
					resultHandler.onResult(new TdApi.Error());
				}
				return;
			}
			if (resultHandler != null) resultHandler.onResult(new TdApi.Ok());
			emitAuthorizationState(new TdApi.AuthorizationStateReady());
			return;
		}
		if (request instanceof TdApi.Close) {
			if (resultHandler != null) resultHandler.onResult(new TdApi.Ok());
			emitAuthorizationState(new TdApi.AuthorizationStateClosed());
			return;
		}
		if (resultHandler != null) resultHandler.onResult(new TdApi.Ok());
	}

	public static void resetTestState() {
		sentRequestNames.clear();
		authorizationUpdateDelaySequenceMs.clear();
		authorizationUpdateDelayMs = 0L;
		lastPhoneNumber = "";
		lastDatabaseDirectory = "";
		lastFilesDirectory = "";
	}

	public static void setAuthorizationUpdateDelayMs(long delayMs) {
		authorizationUpdateDelayMs = delayMs;
	}

	public static void setAuthorizationUpdateDelaySequenceMs(long... delayMs) {
		authorizationUpdateDelaySequenceMs.clear();
		for (long delay : delayMs) authorizationUpdateDelaySequenceMs.add(delay);
	}

	public static List<String> getSentRequestNames() {
		return new ArrayList<>(sentRequestNames);
	}

	public static String getLastPhoneNumber() {
		return lastPhoneNumber;
	}

	public static String getLastDatabaseDirectory() {
		return lastDatabaseDirectory;
	}

	public static String getLastFilesDirectory() {
		return lastFilesDirectory;
	}

	private void emitAuthorizationState(Object authorizationState) {
		long delayMs = getAuthorizationUpdateDelayMs();
		Runnable emit = () -> updateHandler.onResult(
				new TdApi.UpdateAuthorizationState(authorizationState));
		if (delayMs <= 0L) {
			emit.run();
			return;
		}
		new Thread(() -> {
			try {
				Thread.sleep(delayMs);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			emit.run();
		}).start();
	}

	private static long getAuthorizationUpdateDelayMs() {
		if (!authorizationUpdateDelaySequenceMs.isEmpty()) {
			return authorizationUpdateDelaySequenceMs.remove(0);
		}
		return authorizationUpdateDelayMs;
	}
}
