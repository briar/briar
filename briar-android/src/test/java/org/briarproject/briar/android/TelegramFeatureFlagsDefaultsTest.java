package org.briarproject.briar.android;
import org.junit.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static junit.framework.Assert.assertTrue;
public class TelegramFeatureFlagsDefaultsTest {
	@Test
	public void testFeatureFlagsExposeTelegramConnectorGate() throws IOException {
		assertFileContains("../bramble-api/src/main/java/org/briarproject/bramble/api/FeatureFlags.java", "boolean shouldEnableTelegramConnector();");
	}
	@Test
	public void testAndroidAndHeadlessDefaultTelegramConnectorToDisabled() throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/AppModule.java", "public boolean shouldEnableTelegramConnector() {\n\t\t\t\treturn false;");
		assertFileContains("../briar-headless/src/main/java/org/briarproject/briar/headless/HeadlessModule.kt", "override fun shouldEnableTelegramConnector() = false");
	}
	@Test
	public void testTestFeatureFlagsDefaultTelegramConnectorToDisabled() throws IOException {
		assertFileContains("../bramble-core/src/test/java/org/briarproject/bramble/test/TestFeatureFlagModule.java", "public boolean shouldEnableTelegramConnector() {\n\t\t\t\treturn false;");
	}
	@Test
	public void testTelegramConnectorStubIsWiredIntoCoreGraph() throws IOException {
		assertFileContains("../briar-api/src/main/java/org/briarproject/briar/api/telegram/TelegramConnector.java", "boolean isEnabled();");
		assertFileContains("../briar-core/src/main/java/org/briarproject/briar/telegram/TelegramModule.java",
				"if (featureFlags.shouldEnableTelegramConnector()) {\n\t\t\treturn new StubTelegramConnector();\n\t\t}\n\t\treturn new NoOpTelegramConnector();");
		assertFileContains("../briar-core/src/main/java/org/briarproject/briar/BriarCoreModule.java", "TelegramModule.class,");
		assertFileContains("src/main/java/org/briarproject/briar/android/AndroidComponent.java", "TelegramConnector telegramConnector();");
	}
	@Test
	public void testTelegramAuthSessionSeamIsWiredWithoutTdlibTypes() throws IOException {
		assertFileContains("../briar-api/src/main/java/org/briarproject/briar/api/telegram/TelegramAuthState.java", "public enum TelegramAuthState {\n\tIDENTIFIER_ENTRY,\n\tCODE_ENTRY,\n\tPASSWORD_ENTRY,\n\tREADY,\n\tCLOSED,\n\tRECOVERABLE_ERROR\n}");
		assertFileContains("../briar-api/src/main/java/org/briarproject/briar/api/telegram/TelegramAuthSession.java", "public interface TelegramAuthSession {\n\tenum RecoverableErrorDetail {\n\t\tNONE,\n\t\tMISSING_TDLIB,\n\t\tINVALID_IDENTIFIER,\n\t\tINVALID_CODE,\n\t\tINVALID_PASSWORD\n\t}\n\tTelegramAuthState getCurrentState();\n\tRecoverableErrorDetail getRecoverableErrorDetail();\n\tvoid start();\n\tvoid submitIdentifier(String identifier);\n\tvoid submitCode(String code);\n\tvoid submitPassword(String password);\n\tvoid close();\n}");
		assertFileContains("../briar-core/src/main/java/org/briarproject/briar/telegram/TelegramModule.java", "TelegramAuthSession provideTelegramAuthSession(FeatureFlags featureFlags) {");
		assertFileContains("../briar-core/src/main/java/org/briarproject/briar/telegram/TelegramAuthSessionImpl.java", "class TelegramAuthSessionImpl implements TelegramAuthSession {");
		assertFileContains("../briar-core/src/main/java/org/briarproject/briar/telegram/TelegramModule.java",
				"if (featureFlags.shouldEnableTelegramConnector()) {\n\t\t\treturn new TelegramAuthSessionImpl(\n\t\t\t\t\tnew StubTelegramTdlibLoginClient());\n\t\t}\n\t\treturn new TelegramAuthSessionImpl(new NoOpTelegramTdlibLoginClient());");
	}
	@Test
	public void testTelegramAuthSessionUsesHarborOwnedTdlibFacade() throws IOException {
		assertFileContains("../briar-core/src/main/java/org/briarproject/briar/telegram/TelegramTdlibLoginClient.java", "interface TelegramTdlibLoginClient {\n\n\tTelegramAuthState start();\n\n\tRecoverableErrorDetail getRecoverableErrorDetail();\n\n\tTelegramAuthState submitIdentifier(String identifier);\n\n\tTelegramAuthState submitCode(String code);\n\n\tTelegramAuthState submitPassword(String password);\n\n\tTelegramAuthState close();\n}");
		assertFileContainsAll("../briar-core/src/main/java/org/briarproject/briar/telegram/TelegramAuthSessionImpl.java",
				"public RecoverableErrorDetail getRecoverableErrorDetail() {\n\t\treturn tdlibLoginClient.getRecoverableErrorDetail();\n\t}",
				"return recoverableError(RecoverableErrorDetail.MISSING_TDLIB);",
				"return recoverableError(RecoverableErrorDetail.INVALID_IDENTIFIER);",
				"case \"AuthorizationStateWaitTdlibParameters\":\n\t\t\tcase \"AuthorizationStateWaitPhoneNumber\":\n\t\t\t\treturn clearRecoverableErrorDetail(TelegramAuthState.IDENTIFIER_ENTRY);",
				"send(createSetTdlibParametersRequest());",
				"sendReturnsError(createSetAuthenticationPhoneNumberRequest(identifier))",
				"sendReturnsError(createCheckAuthenticationCodeRequest(code))", "return recoverableError(RecoverableErrorDetail.INVALID_CODE);",
				"sendReturnsError(createCheckAuthenticationPasswordRequest(password))",
				"return recoverableError(RecoverableErrorDetail.INVALID_PASSWORD);",
				"private void closeTdlibClient() {\n\t\tlastAuthorizationStateClassName = \"\";\n\t\tauthorizationStateClassName.set(\"\");\n\t\tif (tdlibClient == null) return;");
	}
	@Test
	public void testBriarAndroidCanConsumePrebuiltTdlibAndroidArtifacts() throws IOException {
		assertFileContains("build.gradle", "def tdlibDir = rootProject.file('third_party/tdlib')\ndef tdlibJavaDir = new File(tdlibDir, 'java')\ndef tdlibJniLibsDir = new File(tdlibDir, 'libs')");
		assertFileContains("build.gradle", "java.srcDirs += [tdlibJavaDir]\n\t\t\tjniLibs.srcDirs += [tdlibJniLibsDir]");
	}
	@Test
	public void testConnectionsSettingsCanObserveTelegramConnectorAvailability() throws IOException {
		assertConnectionsFragmentContainsAll(
				"TelegramConnector telegramConnector;",
				"telegramStatus.setVisible(telegramConnector.isEnabled());",
				"telegramStatus.setSummary(requireSettingsActivity()\n\t\t\t\t.isTelegramConnectorReady()\n\t\t\t\t? R.string.telegram_connector_settings_ready_summary\n\t\t\t\t: R.string.telegram_connector_settings_summary);");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/SettingsActivity.java", "boolean isTelegramConnectorReady() {\n\t\treturn getBriarController().isTelegramConnectorReady();\n\t}");
		assertFileContains("src/main/res/xml/settings_connections.xml", "android:key=\"pref_key_telegram_status\"");
	}
	@Test
	public void testBriarControllerExposesTelegramConnectorReadinessSeam() throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/controller/BriarController.java", "boolean isTelegramConnectorReady();");
		assertFileContains("src/main/java/org/briarproject/briar/android/controller/BriarControllerImpl.java", "private final TelegramConnector telegramConnector;");
		assertFileContains("src/main/java/org/briarproject/briar/android/controller/BriarControllerImpl.java", "public boolean isTelegramConnectorReady() {\n\t\treturn accountSignedIn() && telegramConnector.isEnabled();\n\t}");
	}
	@Test
	public void testBriarControllerExposesTelegramIdentityStagingSeam() throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/controller/BriarController.java", "void getTelegramLinkedIdentity(ResultHandler<String> handler);");
		assertFileContains("src/main/java/org/briarproject/briar/android/controller/BriarControllerImpl.java", "public void getTelegramLinkedIdentity(ResultHandler<String> handler) {");
		assertFileContains("src/main/java/org/briarproject/briar/android/controller/BriarControllerImpl.java", "handler.onResult(settings.get(\"pref_key_telegram_linked_identity\"));");
	}
	@Test
	public void testBriarActivityLoadsTelegramIdentityDuringResume() throws IOException {
		assertBriarActivityContainsAll(
				"briarController.getTelegramLinkedIdentity(new UiResultHandler<String>(this) {",
				"onTelegramLinkedIdentityAvailable(linkedIdentity);",
				"protected void onTelegramLinkedIdentityAvailable(\n\t\t\t@Nullable String linkedIdentity) {",
				"protected void showTelegramLinkedIdentitySubtitle(\n\t\t\t@Nullable String linkedIdentity) {",
				"if (getBriarController().isTelegramConnectorReady()\n\t\t\t\t&& linkedIdentity != null && !linkedIdentity.isEmpty()) {",
				"actionBar.setSubtitle(getString(\n\t\t\t\t\tR.string.telegram_connector_transports_subtitle,\n\t\t\t\t\tlinkedIdentity));",
				"actionBar.setSubtitle(null);");
	}
	@Test
	public void testTelegramIdentityConsumersSurfaceOutsideSettings() throws IOException {
		assertTelegramSubtitleConsumer("src/main/java/org/briarproject/briar/android/navdrawer/TransportsActivity.java"); assertTelegramSubtitleConsumer("src/main/java/org/briarproject/briar/android/navdrawer/NavDrawerActivity.java"); assertTelegramSubtitleConsumer("src/main/java/org/briarproject/briar/android/hotspot/HotspotActivity.java"); assertTelegramSubtitleConsumer("src/main/java/org/briarproject/briar/android/contact/add/remote/PendingContactListActivity.java"); assertTelegramSubtitleConsumer("src/main/java/org/briarproject/briar/android/contact/add/remote/AddContactActivity.java"); assertTelegramSubtitleConsumer("src/main/java/org/briarproject/briar/android/contact/add/nearby/AddNearbyContactActivity.java"); assertTelegramSubtitleConsumer("src/main/java/org/briarproject/briar/android/introduction/IntroductionActivity.java");
	}
	@Test
	public void testPasswordFragmentExposesTelegramLoginPlaceholder() throws IOException {
		assertFileContainsAll("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"private final FeatureFlags featureFlags;",
				"FeatureFlags featureFlags,\n\t\t\tTelegramAuthSession telegramAuthSession) {",
				"this.featureFlags = featureFlags;",
				"this.telegramAuthSession = telegramAuthSession;",
				"boolean shouldShowTelegramLogin() {\n\t\treturn featureFlags.shouldEnableTelegramConnector();\n\t}");
		assertFileContainsAll("src/main/java/org/briarproject/briar/android/login/PasswordFragment.java",
				"telegramLoginButton = v.findViewById(R.id.btn_telegram_login);",
				"telegramLoginButton.setVisibility(\n\t\t\t\tviewModel.shouldShowTelegramLogin() ? VISIBLE : GONE);",
				"telegramLoginButton.setOnClickListener(\n\t\t\t\tview -> onTelegramLoginClick());");
		assertFileContains("src/main/res/layout/fragment_password.xml", "android:id=\"@+id/btn_telegram_login\"");
	}
	@Test
	public void testStartupActivityOwnsTelegramLoginPlaceholderRouting() throws IOException {
		assertStartupViewModelContainsAll(
				"enum State {SIGNED_OUT, TELEGRAM_LOGIN, SIGNED_IN, STARTING, MIGRATING, COMPACTING, STARTED}",
				"void showTelegramLoginPlaceholder() {\n\t\tpendingTelegramLinkedIdentity = telegramLoginCode = telegramLoginPassword = \"\";\n\t\ttelegramAuthSession.start();\n\t\ttelegramAuthState.setValue(telegramAuthSession.getCurrentState());\n\t\tstate.setValue(TELEGRAM_LOGIN);\n\t}",
				"void showPasswordFragment() {\n\t\ttelegramLoginCode = telegramLoginPassword = \"\";\n\t\ttelegramAuthSession.close();\n\t\ttelegramAuthState.setValue(telegramAuthSession.getCurrentState());\n\t\tstate.setValue(SIGNED_OUT);\n\t}");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/PasswordFragment.java",
				"private void onTelegramLoginClick() {\n\t\tviewModel.showTelegramLoginPlaceholder();\n\t}");
		assertStartupActivityContainsAll(
				"if (state == SIGNED_OUT) {",
				"showPasswordFragment();",
				"} else if (state == TELEGRAM_LOGIN) {\n\t\t\tshowTelegramLoginPlaceholder();\n\t\t}",
				"if (viewModel.getState().getValue() == TELEGRAM_LOGIN) {\n\t\t\tif (viewModel.isShowingTelegramLoginConfirmation()) {\n\t\t\t\tviewModel.showTelegramLoginIdentifierStep();\n\t\t\t\treturn;\n\t\t\t}\n\t\t\tviewModel.showPasswordFragment();\n\t\t\treturn;\n\t\t}",
				"private void showPasswordFragment() {",
				"private void showTelegramLoginPlaceholder() {");
	}
	@Test
	public void testStartupViewModelClosesTelegramAuthSessionWhenCleared() throws IOException {
		assertStartupViewModelContainsAll(
				"@Override\n\tprotected void onCleared() {\n\t\ttelegramAuthSession.close();\n\t\teventBus.removeListener(this);\n\t}");
	}
	@Test
	public void testTelegramLoginPlaceholderStagesIdentifierInput() throws IOException {
		assertStartupViewModelContainsAll(
				"private String telegramLoginIdentifier = \"\";",
				"String getTelegramLoginIdentifier() {\n\t\treturn telegramLoginIdentifier;\n\t}",
				"void setTelegramLoginIdentifier(String identifier) {\n\t\ttelegramLoginIdentifier = identifier;\n\t}");
		assertTelegramLoginPlaceholderFragmentContainsAll(
				"TextInputEditText identifier =\n\t\t\t\tv.findViewById(R.id.telegram_login_identifier);",
				"identifier.setText(viewModel.getTelegramLoginIdentifier());",
				"viewModel.setTelegramLoginIdentifier(s.toString());");
	}
	@Test
	public void testTelegramLoginPlaceholderStagesCodeEntryStep() throws IOException {
		assertFileContainsAll("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"private String telegramLoginCode = \"\";",
				"String getTelegramLoginCode() {\n\t\treturn telegramLoginCode;\n\t}",
				"void setTelegramLoginCode(String code) {\n\t\ttelegramLoginCode = code;\n\t}",
				"telegramAuthSession.submitCode(telegramLoginCode.trim());",
				"telegramAuthState.setValue(telegramAuthSession.getCurrentState());",
				"telegramLoginCode = telegramLoginPassword = \"\";\n\t\ttelegramAuthSession.close();\n\t\ttelegramAuthSession.start();");
		assertFileContainsAll("src/main/java/org/briarproject/briar/android/login/TelegramLoginPlaceholderFragment.java",
				"View codeEntryStep = v.findViewById(R.id.telegram_login_code_step);",
				"TextInputEditText code =\n\t\t\t\tv.findViewById(R.id.telegram_login_code);",
				"viewModel.setTelegramLoginCode(s.toString());",
				"v.findViewById(R.id.btn_telegram_login_code_continue)\n\t\t\t\t.setOnClickListener(view -> {\n\t\t\t\t\tviewModel.submitTelegramLoginCode();\n\t\t\t\t});");
		assertFileContainsAll("src/main/res/layout/fragment_telegram_login_placeholder.xml",
				"android:id=\"@+id/telegram_login_code\"");
	}
	@Test
	public void testTelegramLoginPlaceholderStagesPasswordEntryStep() throws IOException {
		assertFileContainsAll("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"private String telegramLoginPassword = \"\";",
				"String getTelegramLoginPassword() {\n\t\treturn telegramLoginPassword;\n\t}",
				"void setTelegramLoginPassword(String password) {\n\t\ttelegramLoginPassword = password;\n\t}",
				"void submitTelegramLoginPassword() {\n\t\ttelegramAuthSession.submitPassword(telegramLoginPassword);\n\t\ttelegramAuthState.setValue(telegramAuthSession.getCurrentState());\n\t\tif (telegramAuthState.getValue() != TelegramAuthState.RECOVERABLE_ERROR ||\n\t\t\t\tgetTelegramRecoverableErrorDetail() != RecoverableErrorDetail.INVALID_PASSWORD) {\n\t\t\ttelegramLoginPassword = \"\";\n\t\t}\n\t}");
		assertFileContainsAll("src/main/java/org/briarproject/briar/android/login/TelegramLoginPlaceholderFragment.java",
				"View passwordEntryStep =\n\t\t\t\tv.findViewById(R.id.telegram_login_password_step);",
				"TextInputEditText password =\n\t\t\t\tv.findViewById(R.id.telegram_login_password);",
				"viewModel.setTelegramLoginPassword(s.toString());",
				"v.findViewById(R.id.btn_telegram_login_password_continue)\n\t\t\t\t.setOnClickListener(view -> {\n\t\t\t\t\tviewModel.submitTelegramLoginPassword();\n\t\t\t\t});");
		assertFileContainsAll("src/main/res/layout/fragment_telegram_login_placeholder.xml",
				"android:id=\"@+id/telegram_login_password\"");
	}
	@Test
	public void testTelegramLoginPlaceholderStagesConfirmationStep() throws IOException {
		assertStartupViewModelContainsAll(
				"private final MutableLiveData<TelegramAuthState> telegramAuthState =\n\t\t\tnew MutableLiveData<>(TelegramAuthState.CLOSED);",
				"LiveData<TelegramAuthState> getTelegramAuthState() {\n\t\treturn telegramAuthState;\n\t}",
				"RecoverableErrorDetail getTelegramRecoverableErrorDetail() {\n\t\treturn telegramAuthSession.getRecoverableErrorDetail();\n\t}",
				"void submitTelegramLoginIdentifier() {\n\t\ttelegramLoginCode = telegramLoginPassword = \"\";\n\t\ttelegramAuthSession.submitIdentifier(telegramLoginIdentifier.trim());\n\t\ttelegramAuthState.setValue(telegramAuthSession.getCurrentState());\n\t}",
				"void showTelegramLoginIdentifierStep() {\n\t\ttelegramLoginCode = telegramLoginPassword = \"\";\n\t\ttelegramAuthSession.close();\n\t\ttelegramAuthSession.start();\n\t\ttelegramAuthState.setValue(telegramAuthSession.getCurrentState());\n\t}",
				"boolean isShowingTelegramLoginConfirmation() {\n\t\tTelegramAuthState authState = telegramAuthState.getValue();\n\t\treturn authState == TelegramAuthState.CODE_ENTRY ||\n\t\t\t\tauthState == TelegramAuthState.PASSWORD_ENTRY ||\n\t\t\t\tauthState == TelegramAuthState.READY ||\n\t\t\t\tauthState == TelegramAuthState.RECOVERABLE_ERROR &&\n\t\t\t\t\t\t(getTelegramRecoverableErrorDetail() == RecoverableErrorDetail.INVALID_CODE ||\n\t\t\t\t\t\t\t\tgetTelegramRecoverableErrorDetail() == RecoverableErrorDetail.INVALID_PASSWORD);\n\t}");
		assertStartupActivityContainsAll(
				"if (viewModel.isShowingTelegramLoginConfirmation()) {\n\t\t\t\tviewModel.showTelegramLoginIdentifierStep();\n\t\t\t\treturn;\n\t\t\t}");
		assertTelegramLoginPlaceholderFragmentContainsAll(
				"v.findViewById(R.id.btn_telegram_login_continue)\n\t\t\t\t.setOnClickListener(view -> {",
				"viewModel.submitTelegramLoginIdentifier();",
				"v.findViewById(R.id.btn_telegram_login_confirmation_back)\n\t\t\t\t.setOnClickListener(view -> {",
				"viewModel.showTelegramLoginIdentifierStep();",
				"} else if (authState == TelegramAuthState.PASSWORD_ENTRY ||\n\t\t\t\tauthState == TelegramAuthState.RECOVERABLE_ERROR &&\n\t\t\t\t\t\tviewModel.getTelegramRecoverableErrorDetail()\n\t\t\t\t\t\t== RecoverableErrorDetail.INVALID_PASSWORD) {\n\t\t\tidentifierStep.setVisibility(View.GONE);\n\t\t\tcodeEntryStep.setVisibility(View.GONE);\n\t\t\tpasswordEntryStep.setVisibility(View.VISIBLE);\n\t\t\tconfirmationStep.setVisibility(View.GONE);",
				"} else if (authState == TelegramAuthState.READY) {\n\t\t\tidentifierStep.setVisibility(View.GONE);\n\t\t\tcodeEntryStep.setVisibility(View.GONE);\n\t\t\tpasswordEntryStep.setVisibility(View.GONE);\n\t\t\tconfirmationStep.setVisibility(View.VISIBLE);",
				"message.setText(getLoginMessage(authState));",
				"private int getLoginMessage(TelegramAuthState authState) {\n\t\tif (authState != TelegramAuthState.RECOVERABLE_ERROR) {\n\t\t\treturn R.string.telegram_connector_login_message;\n\t\t}\n\t\tRecoverableErrorDetail detail =\n\t\t\t\tviewModel.getTelegramRecoverableErrorDetail();\n\t\tif (detail == RecoverableErrorDetail.MISSING_TDLIB) return R.string.telegram_connector_login_tdlib_missing_message;\n\t\tif (detail == RecoverableErrorDetail.INVALID_IDENTIFIER) return R.string.telegram_connector_login_identifier_invalid_message;\n\t\tif (detail == RecoverableErrorDetail.INVALID_PASSWORD) return R.string.telegram_connector_login_password_invalid_message;\n\t\treturn detail == RecoverableErrorDetail.INVALID_CODE\n\t\t\t\t? R.string.telegram_connector_login_code_invalid_message\n\t\t\t\t: R.string.telegram_connector_login_retry_message;\n\t}",
				"confirmationMessage.setText(getString(\n\t\t\t\t\tR.string.telegram_connector_login_confirmation_message,\n\t\t\t\t\tviewModel.getTelegramLoginIdentifier().trim()));");
	}
	@Test
	public void testTelegramLoginCodeRetryKeepsCodeEntryVisible() throws IOException {
		assertTelegramLoginPlaceholderFragmentContainsAll("if (authState == TelegramAuthState.CODE_ENTRY || authState == TelegramAuthState.RECOVERABLE_ERROR && viewModel.getTelegramRecoverableErrorDetail() == RecoverableErrorDetail.INVALID_CODE) {");
	}
	@Test
	public void testStartupViewModelClearsTelegramCodeAfterAdvancingPastCodeStep() throws IOException {
		assertStartupViewModelContainsAll(
				"void submitTelegramLoginCode() {\n\t\ttelegramAuthSession.submitCode(telegramLoginCode.trim());\n\t\ttelegramAuthState.setValue(telegramAuthSession.getCurrentState());\n\t\tif (telegramAuthState.getValue() != TelegramAuthState.RECOVERABLE_ERROR ||\n\t\t\t\tgetTelegramRecoverableErrorDetail() != RecoverableErrorDetail.INVALID_CODE) {\n\t\t\ttelegramLoginCode = \"\";\n\t\t}\n\t}");
	}
	@Test
	public void testStartupViewModelClearsTelegramPasswordAfterAdvancingPastPasswordStep() throws IOException {
		assertStartupViewModelContainsAll(
				"void submitTelegramLoginPassword() {\n\t\ttelegramAuthSession.submitPassword(telegramLoginPassword);\n\t\ttelegramAuthState.setValue(telegramAuthSession.getCurrentState());\n\t\tif (telegramAuthState.getValue() != TelegramAuthState.RECOVERABLE_ERROR ||\n\t\t\t\tgetTelegramRecoverableErrorDetail() != RecoverableErrorDetail.INVALID_PASSWORD) {\n\t\t\ttelegramLoginPassword = \"\";\n\t\t}\n\t}");
	}
	@Test
	public void testTelegramLoginMissingTdlibDisablesIdentifierContinue() throws IOException {
		assertTelegramLoginPlaceholderFragmentContainsAll(
				"if (authState == TelegramAuthState.RECOVERABLE_ERROR &&\n\t\t\t\tviewModel.getTelegramRecoverableErrorDetail()\n\t\t\t\t== RecoverableErrorDetail.MISSING_TDLIB) {\n\t\t\tcontinueButton.setEnabled(false);\n\t\t}");
	}
	@Test
	public void testTelegramLoginCompletionStagesLinkedIdentityAfterPasswordSignIn() throws IOException {
		assertStartupViewModelContainsAll(
				"void completeTelegramLoginConfirmation() {\n\t\tpendingTelegramLinkedIdentity = telegramLoginIdentifier.trim();\n\t\tshowPasswordFragment();\n\t}",
				"accountManager.signIn(password);\n\t\t\t\tstorePendingTelegramLinkedIdentity();\n\t\t\t\tpasswordValidated.postEvent(SUCCESS);",
				"private void storePendingTelegramLinkedIdentity() {\n\t\tif (pendingTelegramLinkedIdentity.isEmpty()) return;",
				"settingsManager.mergeSettings(settings, SETTINGS_NAMESPACE);");
	}
	@Test
	public void testStartupActivityShowsTelegramIdentityHandoffConfirmation() throws IOException {
		assertStartupViewModelContainsAll(
				"LiveEvent<String> getTelegramLinkedIdentityStaged() {\n\t\treturn telegramLinkedIdentityStaged;\n\t}",
				"telegramLinkedIdentityStaged.postEvent(lastTelegramLinkedIdentityStaged);");
		assertStartupActivityContainsAll(
				"viewModel.getTelegramLinkedIdentityStaged().observeEvent(this,\n\t\t\t\tidentifier -> {\n\t\t\t\t\tstagedTelegramLoginIdentity = identifier;\n\t\t\t\t\tToast.makeText(this,\n\t\t\t\t\t\t\tgetString(\n\t\t\t\t\t\t\t\t\tR.string.telegram_connector_login_handoff_staged,\n\t\t\t\t\t\t\t\t\tidentifier),\n\t\t\t\t\t\t\tLENGTH_LONG).show();\n\t\t\t\t});");
	}
	@Test
	public void testStartupActivityPersistsTelegramIdentityHandoffAcrossRecreation() throws IOException {
		assertStartupActivityContainsAll(
				"private static final String KEY_STAGED_TELEGRAM_LOGIN_IDENTITY =\n\t\t\t\"stagedTelegramLoginIdentity\";",
				"if (state != null) {\n\t\t\tstagedTelegramLoginIdentity = state.getString(\n\t\t\t\t\tKEY_STAGED_TELEGRAM_LOGIN_IDENTITY, \"\");\n\t\t}",
				"protected void onSaveInstanceState(Bundle state) {\n\t\tsuper.onSaveInstanceState(state);\n\t\tstate.putString(KEY_STAGED_TELEGRAM_LOGIN_IDENTITY,\n\t\t\t\tstagedTelegramLoginIdentity);\n\t}");
	}
	@Test
	public void testStartupLoginCanOfferTelegramSetupEntrypointAfterFreshHandoff() throws IOException {
		assertStartupActivityContainsAll(
				"public static final String EXTRA_STAGED_TELEGRAM_LOGIN_IDENTITY =\n\t\t\t\"briar.STAGED_TELEGRAM_LOGIN_IDENTITY\";",
				"if (stagedTelegramLoginIdentity.isEmpty()) {\n\t\t\t\tstagedTelegramLoginIdentity =\n\t\t\t\t\t\tviewModel.getLastTelegramLinkedIdentityStaged();\n\t\t\t}",
				"result.putExtra(EXTRA_STAGED_TELEGRAM_LOGIN_IDENTITY,\n\t\t\t\t\t\tstagedTelegramLoginIdentity);");
		assertBriarActivityContainsAll(
				"private static final String EXTRA_PENDING_TELEGRAM_LOGIN_ENTRYPOINT =\n\t\t\t\"briar.PENDING_TELEGRAM_LOGIN_ENTRYPOINT\";",
				"String stagedIdentity = data.getStringExtra(\n\t\t\t\t\t\tStartupActivity.EXTRA_STAGED_TELEGRAM_LOGIN_IDENTITY);",
				"getIntent().putExtra(EXTRA_PENDING_TELEGRAM_LOGIN_ENTRYPOINT,\n\t\t\t\t\t\t\tstagedIdentity);",
				"maybeShowTelegramLoginSetupEntryPoint();",
				"private void maybeShowTelegramLoginSetupEntryPoint() {",
				"getIntent().removeExtra(EXTRA_PENDING_TELEGRAM_LOGIN_ENTRYPOINT);",
				"R.string.telegram_connector_login_entrypoint_message,\n\t\t\t\t\t\tlinkedIdentity))",
				"Intent i = new Intent(this, SettingsActivity.class);",
				"i.setAction(ACTION_MANAGE_NETWORK_USAGE);");
	}
	@Test
	public void testPostSignInTelegramSetupEntrypointCanAutoOpenPlaceholder() throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/SettingsActivity.java",
				"static final String EXTRA_OPEN_TELEGRAM_SETUP = \"openTelegramSetup\";");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/SettingsActivity.java",
				"boolean consumeOpenTelegramSetup() {\n\t\tboolean openTelegramSetup = getIntent().getBooleanExtra(\n\t\t\t\tEXTRA_OPEN_TELEGRAM_SETUP, false);\n\t\tgetIntent().removeExtra(EXTRA_OPEN_TELEGRAM_SETUP);\n\t\treturn openTelegramSetup;\n\t}");
		assertBriarActivityContainsAll(
				"i.putExtra(SettingsActivity.EXTRA_OPEN_TELEGRAM_SETUP, true);");
		assertConnectionsFragmentContainsAll(
				"if (requireSettingsActivity().consumeOpenTelegramSetup()) {",
				"showTelegramSetupDialog(requireSettingsActivity()",
				".isTelegramConnectorReady(), value);");
	}
	@Test
	public void testConnectionsSettingsExposeTelegramSetupPlaceholder() throws IOException {
		assertConnectionsFragmentContainsAll(
				"telegramStatus.setOnPreferenceClickListener(preference -> {",
				"showTelegramSetupDialog(requireSettingsActivity()\n\t\t\t\t\t.isTelegramConnectorReady(),\n\t\t\t\t\ttelegramLinkedIdentity.getText());");
	}
	@Test
	public void testTelegramSetupPlaceholderCanContinueToIdentityReview() throws IOException {
		assertConnectionsFragmentContainsAll(
				"builder.setPositiveButton(ready\n\t\t\t\t? R.string.telegram_connector_setup_continue_button\n\t\t\t\t: R.string.ok,",
				"if (ready) showTelegramIdentityEditor();",
				"private void showTelegramIdentityEditor() {\n\t\ttelegramLinkedIdentity.performClick();\n\t}");
	}
	@Test
	public void testTelegramIdentityReviewCanContinueToVerificationPlaceholder() throws IOException {
		assertConnectionsFragmentContainsAll(
				"static final String PREF_KEY_TELEGRAM_VERIFICATION = \"pref_key_telegram_verification\";",
				"private Preference telegramVerification;",
				"telegramVerification = findPreference(PREF_KEY_TELEGRAM_VERIFICATION);",
				"telegramVerification.setVisible(telegramConnector.isEnabled());",
				"telegramVerification.setOnPreferenceClickListener(preference -> {",
				"updateTelegramVerificationState(telegramLinkedIdentity.getText());",
				"showTelegramVerificationDialog(telegramLinkedIdentity.getText());",
				"telegramVerification.setEnabled(requireSettingsActivity().isTelegramConnectorReady()\n\t\t\t\t\t\t\t&& !isNullOrEmpty(value));",
				"private void updateTelegramVerificationState(@Nullable String linkedIdentity) {",
				"private void showTelegramVerificationDialog(@Nullable String linkedIdentity) {");
		assertFileContains("src/main/res/xml/settings_connections.xml",
				"android:key=\"pref_key_telegram_verification\"");
	}
	@Test
	public void testTelegramVerificationPlaceholderCanContinueToAuthenticationPlaceholder() throws IOException {
		assertConnectionsFragmentContainsAll(
				".setPositiveButton(\n\t\t\t\t\t\tR.string.telegram_connector_verification_continue_button,",
				"showTelegramAuthenticationPlaceholder(linkedIdentity))",
				".setNegativeButton(R.string.cancel, null)",
				"private void showTelegramAuthenticationPlaceholder(String linkedIdentity) {");
	}
	@Test
	public void testTelegramAuthPlaceholderCompletionUpdatesVerificationSummary() throws IOException {
		assertConnectionsFragmentContainsAll(
				"private String telegramAuthenticationPlaceholderCompletedIdentity;",
				"telegramAuthenticationPlaceholderCompletedIdentity = linkedIdentity;",
				"updateTelegramVerificationState(linkedIdentity);",
				"linkedIdentity.equals(telegramAuthenticationPlaceholderCompletedIdentity)");
	}
	@Test
	public void testTelegramVerificationCompletionResetsWhenIdentityChanges() throws IOException {
		assertConnectionsFragmentContainsAll(
				"clearTelegramVerificationCompletionIfIdentityChanged(value);",
				"private void clearTelegramVerificationCompletionIfIdentityChanged(\n\t\t\t@Nullable String linkedIdentity) {",
				"if (isNullOrEmpty(telegramAuthenticationPlaceholderCompletedIdentity)) return;",
				"if (!telegramAuthenticationPlaceholderCompletedIdentity.equals(linkedIdentity)) {",
				"telegramAuthenticationPlaceholderCompletedIdentity = null;");
	}
	@Test
	public void testConnectionsSettingsExposeTelegramIdentityLinkingSeam() throws IOException {
		assertConnectionsFragmentContainsAll(
				"static final String PREF_KEY_TELEGRAM_LINKED_IDENTITY =\n\t\t\t\"pref_key_telegram_linked_identity\";",
				"telegramLinkedIdentity.setPreferenceDataStore(viewModel.settingsStore);",
				"telegramLinkedIdentity.setSummaryProvider(preference -> {",
				"viewModel.getTelegramLinkedIdentity().observe(lifecycleOwner,\n\t\t\t\tvalue -> {",
				"telegramLinkedIdentity.setText(value);");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/SettingsViewModel.java",
				"private final MutableLiveData<String> telegramLinkedIdentity =\n\t\t\tnew MutableLiveData<>();");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/SettingsViewModel.java",
				"telegramLinkedIdentity.postValue(settings.get(\n\t\t\t\tConnectionsFragment.PREF_KEY_TELEGRAM_LINKED_IDENTITY));");
		assertFileContains("src/main/res/xml/settings_connections.xml",
				"android:key=\"pref_key_telegram_linked_identity\"");
	}
	private static void assertFileContains(String moduleRelativePath, String expectedText) throws IOException {
		String contents = new String(Files.readAllBytes(resolveModulePath(moduleRelativePath)), StandardCharsets.UTF_8);
		assertTrue("Expected to find '" + expectedText + "' in " + moduleRelativePath, contents.contains(expectedText));
	}
	private static void assertFileContainsAll(String moduleRelativePath, String... expectedTexts) throws IOException { for (String expectedText : expectedTexts) assertFileContains(moduleRelativePath, expectedText); }
	private static void assertTelegramSubtitleConsumer(String moduleRelativePath) throws IOException { assertFileContains(moduleRelativePath, "protected void onTelegramLinkedIdentityAvailable(\n\t\t\t@Nullable String linkedIdentity) {"); assertFileContains(moduleRelativePath, "showTelegramLinkedIdentitySubtitle(linkedIdentity);"); }
	private static void assertBriarActivityContainsAll(String... expectedTexts) throws IOException { assertFileContainsAll("src/main/java/org/briarproject/briar/android/activity/BriarActivity.java", expectedTexts); }
	private static void assertConnectionsFragmentContainsAll(String... expectedTexts) throws IOException { assertFileContainsAll("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java", expectedTexts); }
	private static void assertStartupActivityContainsAll(String... expectedTexts) throws IOException { assertFileContainsAll("src/main/java/org/briarproject/briar/android/login/StartupActivity.java", expectedTexts); }
	private static void assertStartupViewModelContainsAll(String... expectedTexts) throws IOException { assertFileContainsAll("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java", expectedTexts); }
	private static void assertTelegramLoginPlaceholderFragmentContainsAll(String... expectedTexts) throws IOException { assertFileContainsAll("src/main/java/org/briarproject/briar/android/login/TelegramLoginPlaceholderFragment.java", expectedTexts); }
	private static Path resolveModulePath(String moduleRelativePath) {
		Path cwd = Paths.get("").toAbsolutePath().normalize();
		Path direct = cwd.resolve(moduleRelativePath).normalize(); if (Files.exists(direct)) return direct;
		Path nested = cwd.resolve("briar-android").resolve(moduleRelativePath).normalize();
		if (Files.exists(nested)) return nested;
		throw new IllegalStateException("Could not resolve module path: " + moduleRelativePath + " from " + cwd);
	}
}
