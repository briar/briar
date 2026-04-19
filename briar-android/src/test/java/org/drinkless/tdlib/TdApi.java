package org.drinkless.tdlib;

@SuppressWarnings("unused")
public abstract class TdApi {

	public abstract static class Function {
	}

	public static class Ok {
	}

	public static class Error {
	}

	public static class UpdateAuthorizationState {
		public final Object authorizationState;

		public UpdateAuthorizationState(Object authorizationState) {
			this.authorizationState = authorizationState;
		}
	}

	public static class AuthorizationStateWaitTdlibParameters {
	}

	public static class AuthorizationStateWaitPhoneNumber {
	}

	public static class AuthorizationStateWaitCode {
	}

	public static class AuthorizationStateWaitPassword {
	}

	public static class AuthorizationStateReady {
	}

	public static class AuthorizationStateClosed {
	}

	public static class SetTdlibParameters extends Function {
		public boolean useTestDc;
		public String databaseDirectory;
		public String filesDirectory;
		public byte[] databaseEncryptionKey;
		public boolean useFileDatabase;
		public boolean useChatInfoDatabase;
		public boolean useMessageDatabase;
		public boolean useSecretChats;
		public int apiId;
		public String apiHash;
		public String systemLanguageCode;
		public String deviceModel;
		public String systemVersion;
		public String applicationVersion;
	}

	public static class PhoneNumberAuthenticationSettings {
	}

	public static class SetAuthenticationPhoneNumber extends Function {
		public final String phoneNumber;
		public final PhoneNumberAuthenticationSettings settings;

		public SetAuthenticationPhoneNumber(String phoneNumber,
				PhoneNumberAuthenticationSettings settings) {
			this.phoneNumber = phoneNumber;
			this.settings = settings;
		}
	}

	public static class CheckAuthenticationCode extends Function {
		public final String code;

		public CheckAuthenticationCode(String code) {
			this.code = code;
		}
	}

	public static class Close extends Function {
	}
}
