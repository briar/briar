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
	private static String lastPhoneNumber = "";

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
		lastPhoneNumber = "";
	}

	public static List<String> getSentRequestNames() {
		return new ArrayList<>(sentRequestNames);
	}

	public static String getLastPhoneNumber() {
		return lastPhoneNumber;
	}

	private void emitAuthorizationState(Object authorizationState) {
		updateHandler.onResult(
				new TdApi.UpdateAuthorizationState(authorizationState));
	}
}
