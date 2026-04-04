package org.briarproject.briar.android;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class TelegramFeatureFlagsDefaultsTest {

	@Test
	public void testFeatureFlagsExposeTelegramConnectorGate() throws IOException {
		assertFileContains("../bramble-api/src/main/java/org/briarproject/bramble/api/FeatureFlags.java",
				"boolean shouldEnableTelegramConnector();");
	}

	@Test
	public void testAndroidAndHeadlessDefaultTelegramConnectorToDisabled()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/AppModule.java",
				"public boolean shouldEnableTelegramConnector() {\n\t\t\t\treturn false;");
		assertFileContains("../briar-headless/src/main/java/org/briarproject/briar/headless/HeadlessModule.kt",
				"override fun shouldEnableTelegramConnector() = false");
	}

	@Test
	public void testTestFeatureFlagsDefaultTelegramConnectorToDisabled()
			throws IOException {
		assertFileContains("../bramble-core/src/test/java/org/briarproject/bramble/test/TestFeatureFlagModule.java",
				"public boolean shouldEnableTelegramConnector() {\n\t\t\t\treturn false;");
	}

	@Test
	public void testTelegramConnectorStubIsWiredIntoCoreGraph()
			throws IOException {
		assertFileContains("../briar-api/src/main/java/org/briarproject/briar/api/telegram/TelegramConnector.java",
				"boolean isEnabled();");
		assertFileContains("../briar-core/src/main/java/org/briarproject/briar/telegram/TelegramModule.java",
				"if (featureFlags.shouldEnableTelegramConnector()) {\n\t\t\treturn new StubTelegramConnector();\n\t\t}\n\t\treturn new NoOpTelegramConnector();");
		assertFileContains("../briar-core/src/main/java/org/briarproject/briar/BriarCoreModule.java",
				"TelegramModule.class,");
		assertFileContains("src/main/java/org/briarproject/briar/android/AndroidComponent.java",
				"TelegramConnector telegramConnector();");
	}

	@Test
	public void testConnectionsSettingsCanObserveTelegramConnectorAvailability()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"TelegramConnector telegramConnector;");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"telegramStatus.setVisible(telegramConnector.isEnabled());");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"telegramStatus.setSummary(requireSettingsActivity()\n\t\t\t\t.isTelegramConnectorReady()\n\t\t\t\t? R.string.telegram_connector_settings_ready_summary\n\t\t\t\t: R.string.telegram_connector_settings_summary);");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/SettingsActivity.java",
				"boolean isTelegramConnectorReady() {\n\t\treturn getBriarController().isTelegramConnectorReady();\n\t}");
		assertFileContains("src/main/res/xml/settings_connections.xml",
				"android:key=\"pref_key_telegram_status\"");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_settings_title\">Telegram connector</string>");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_settings_ready_summary\">Connector foundations are ready for internal Harbor testing. Telegram syncing is not active yet.</string>");
	}

	@Test
	public void testBriarControllerExposesTelegramConnectorReadinessSeam()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/controller/BriarController.java",
				"boolean isTelegramConnectorReady();");
		assertFileContains("src/main/java/org/briarproject/briar/android/controller/BriarControllerImpl.java",
				"private final TelegramConnector telegramConnector;");
		assertFileContains("src/main/java/org/briarproject/briar/android/controller/BriarControllerImpl.java",
				"public boolean isTelegramConnectorReady() {\n\t\treturn accountSignedIn() && telegramConnector.isEnabled();\n\t}");
	}

	@Test
	public void testBriarControllerExposesTelegramIdentityStagingSeam()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/controller/BriarController.java",
				"void getTelegramLinkedIdentity(ResultHandler<String> handler);");
		assertFileContains("src/main/java/org/briarproject/briar/android/controller/BriarControllerImpl.java",
				"public void getTelegramLinkedIdentity(ResultHandler<String> handler) {");
		assertFileContains("src/main/java/org/briarproject/briar/android/controller/BriarControllerImpl.java",
				"handler.onResult(settings.get(\"pref_key_telegram_linked_identity\"));");
	}

	@Test
	public void testBriarActivityLoadsTelegramIdentityDuringResume()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/activity/BriarActivity.java",
				"briarController.getTelegramLinkedIdentity(new UiResultHandler<String>(this) {");
		assertFileContains("src/main/java/org/briarproject/briar/android/activity/BriarActivity.java",
				"onTelegramLinkedIdentityAvailable(linkedIdentity);");
		assertFileContains("src/main/java/org/briarproject/briar/android/activity/BriarActivity.java",
				"protected void onTelegramLinkedIdentityAvailable(\n\t\t\t@Nullable String linkedIdentity) {");
		assertFileContains("src/main/java/org/briarproject/briar/android/activity/BriarActivity.java",
				"protected void showTelegramLinkedIdentitySubtitle(\n\t\t\t@Nullable String linkedIdentity) {");
		assertFileContains("src/main/java/org/briarproject/briar/android/activity/BriarActivity.java",
				"if (getBriarController().isTelegramConnectorReady()\n\t\t\t\t&& linkedIdentity != null && !linkedIdentity.isEmpty()) {");
		assertFileContains("src/main/java/org/briarproject/briar/android/activity/BriarActivity.java",
				"actionBar.setSubtitle(getString(\n\t\t\t\t\tR.string.telegram_connector_transports_subtitle,\n\t\t\t\t\tlinkedIdentity));");
		assertFileContains("src/main/java/org/briarproject/briar/android/activity/BriarActivity.java",
				"actionBar.setSubtitle(null);");
	}

	@Test
	public void testTransportsActivitySurfacesTelegramIdentityOutsideSettings()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/navdrawer/TransportsActivity.java",
				"protected void onTelegramLinkedIdentityAvailable(\n\t\t\t@Nullable String linkedIdentity) {");
		assertFileContains("src/main/java/org/briarproject/briar/android/navdrawer/TransportsActivity.java",
				"showTelegramLinkedIdentitySubtitle(linkedIdentity);");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_transports_subtitle\">Telegram account staged: %1$s</string>");
	}

	@Test
	public void testNavDrawerActivitySurfacesTelegramIdentityOutsideSettings()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/navdrawer/NavDrawerActivity.java",
				"protected void onTelegramLinkedIdentityAvailable(\n\t\t\t@Nullable String linkedIdentity) {");
		assertFileContains("src/main/java/org/briarproject/briar/android/navdrawer/NavDrawerActivity.java",
				"showTelegramLinkedIdentitySubtitle(linkedIdentity);");
	}

	@Test
	public void testHotspotActivitySurfacesTelegramIdentityOutsideSettings()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/hotspot/HotspotActivity.java",
				"protected void onTelegramLinkedIdentityAvailable(\n\t\t\t@Nullable String linkedIdentity) {");
		assertFileContains("src/main/java/org/briarproject/briar/android/hotspot/HotspotActivity.java",
				"showTelegramLinkedIdentitySubtitle(linkedIdentity);");
	}

	@Test
	public void testPendingContactListActivitySurfacesTelegramIdentityOutsideSettings()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/contact/add/remote/PendingContactListActivity.java",
				"protected void onTelegramLinkedIdentityAvailable(\n\t\t\t@Nullable String linkedIdentity) {");
		assertFileContains("src/main/java/org/briarproject/briar/android/contact/add/remote/PendingContactListActivity.java",
				"showTelegramLinkedIdentitySubtitle(linkedIdentity);");
	}

	@Test
	public void testAddContactActivitySurfacesTelegramIdentityOutsideSettings()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/contact/add/remote/AddContactActivity.java",
				"protected void onTelegramLinkedIdentityAvailable(\n\t\t\t@Nullable String linkedIdentity) {");
		assertFileContains("src/main/java/org/briarproject/briar/android/contact/add/remote/AddContactActivity.java",
				"showTelegramLinkedIdentitySubtitle(linkedIdentity);");
	}

	@Test
	public void testAddNearbyContactActivitySurfacesTelegramIdentityOutsideSettings()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/contact/add/nearby/AddNearbyContactActivity.java",
				"protected void onTelegramLinkedIdentityAvailable(\n\t\t\t@Nullable String linkedIdentity) {");
		assertFileContains("src/main/java/org/briarproject/briar/android/contact/add/nearby/AddNearbyContactActivity.java",
				"showTelegramLinkedIdentitySubtitle(linkedIdentity);");
	}

	@Test
	public void testIntroductionActivitySurfacesTelegramIdentityOutsideSettings()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/introduction/IntroductionActivity.java",
				"protected void onTelegramLinkedIdentityAvailable(\n\t\t\t@Nullable String linkedIdentity) {");
		assertFileContains("src/main/java/org/briarproject/briar/android/introduction/IntroductionActivity.java",
				"showTelegramLinkedIdentitySubtitle(linkedIdentity);");
	}

	@Test
	public void testGroupMemberListActivityDoesNotSurfaceTelegramIdentity()
			throws IOException {
		assertFileNotContains("src/main/java/org/briarproject/briar/android/privategroup/memberlist/GroupMemberListActivity.java",
				"protected void onTelegramLinkedIdentityAvailable(\n\t\t\t@Nullable String linkedIdentity) {");
		assertFileNotContains("src/main/java/org/briarproject/briar/android/privategroup/memberlist/GroupMemberListActivity.java",
				"showTelegramLinkedIdentitySubtitle(linkedIdentity);");
	}

	@Test
	public void testPasswordFragmentExposesTelegramLoginPlaceholder()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"private final FeatureFlags featureFlags;");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"FeatureFlags featureFlags) {");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"this.featureFlags = featureFlags;");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"boolean shouldShowTelegramLogin() {\n\t\treturn featureFlags.shouldEnableTelegramConnector();\n\t}");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/PasswordFragment.java",
				"telegramLoginButton = v.findViewById(R.id.btn_telegram_login);");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/PasswordFragment.java",
				"telegramLoginButton.setVisibility(\n\t\t\t\tviewModel.shouldShowTelegramLogin() ? VISIBLE : GONE);");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/PasswordFragment.java",
				"telegramLoginButton.setOnClickListener(\n\t\t\t\tview -> onTelegramLoginClick());");
		assertFileContains("src/main/res/layout/fragment_password.xml",
				"android:id=\"@+id/btn_telegram_login\"");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_login_button\">Continue with Telegram</string>");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_login_title\">Telegram login</string>");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_login_message\">Telegram login is staged for internal Harbor testing. Briar password sign-in remains the active path in this build.</string>");
	}

	@Test
	public void testStartupActivityOwnsTelegramLoginPlaceholderRouting()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"enum State {SIGNED_OUT, TELEGRAM_LOGIN, SIGNED_IN, STARTING, MIGRATING, COMPACTING, STARTED}");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"void showTelegramLoginPlaceholder() {\n\t\tshowingTelegramLoginConfirmation.setValue(false);\n\t\tstate.setValue(TELEGRAM_LOGIN);\n\t}");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"void showPasswordFragment() {\n\t\tshowingTelegramLoginConfirmation.setValue(false);\n\t\tstate.setValue(SIGNED_OUT);\n\t}");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/PasswordFragment.java",
				"private void onTelegramLoginClick() {\n\t\tviewModel.showTelegramLoginPlaceholder();\n\t}");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupActivity.java",
				"if (state == SIGNED_OUT) {");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupActivity.java",
				"showPasswordFragment();");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupActivity.java",
				"} else if (state == TELEGRAM_LOGIN) {\n\t\t\tshowTelegramLoginPlaceholder();\n\t\t}");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupActivity.java",
				"if (viewModel.getState().getValue() == TELEGRAM_LOGIN) {\n\t\t\tif (viewModel.isShowingTelegramLoginConfirmation()) {\n\t\t\t\tviewModel.showTelegramLoginIdentifierStep();\n\t\t\t\treturn;\n\t\t\t}\n\t\t\tviewModel.showPasswordFragment();\n\t\t\treturn;\n\t\t}");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupActivity.java",
				"private void showPasswordFragment() {");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupActivity.java",
				"private void showTelegramLoginPlaceholder() {");
	}

	@Test
	public void testStartupActivityShowsTelegramLoginPlaceholderFragment()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupActivity.java",
				"private void showTelegramLoginPlaceholder() {\n\t\tshowNextFragment(TelegramLoginPlaceholderFragment.newInstance());\n\t}");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/TelegramLoginPlaceholderFragment.java",
				"public class TelegramLoginPlaceholderFragment extends BaseFragment {");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/TelegramLoginPlaceholderFragment.java",
				"static final String TAG =\n\t\t\tTelegramLoginPlaceholderFragment.class.getName();");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/TelegramLoginPlaceholderFragment.java",
				"static TelegramLoginPlaceholderFragment newInstance() {\n\t\treturn new TelegramLoginPlaceholderFragment();\n\t}");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/TelegramLoginPlaceholderFragment.java",
				"View v = inflater.inflate(R.layout.fragment_telegram_login_placeholder,\n\t\t\t\tcontainer, false);");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/TelegramLoginPlaceholderFragment.java",
				"ViewModelProvider.Factory viewModelFactory;");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/TelegramLoginPlaceholderFragment.java",
				"component.inject(this);");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/TelegramLoginPlaceholderFragment.java",
				"viewModel = new ViewModelProvider(requireActivity(),\n\t\t\t\tviewModelFactory).get(StartupViewModel.class);");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/TelegramLoginPlaceholderFragment.java",
				"v.findViewById(R.id.btn_telegram_login_back)\n\t\t\t\t.setOnClickListener(view -> viewModel.showPasswordFragment());");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/TelegramLoginPlaceholderFragment.java",
				"return TAG;");
		assertFileContains("src/main/java/org/briarproject/briar/android/activity/ActivityComponent.java",
				"void inject(TelegramLoginPlaceholderFragment fragment);");
		assertFileContains("src/main/res/layout/fragment_telegram_login_placeholder.xml",
				"android:id=\"@+id/btn_telegram_login_back\"");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_login_back_button\">Use Harbor password instead</string>");
	}

	@Test
	public void testTelegramLoginPlaceholderStagesIdentifierInput()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"private String telegramLoginIdentifier = \"\";");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"String getTelegramLoginIdentifier() {\n\t\treturn telegramLoginIdentifier;\n\t}");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"void setTelegramLoginIdentifier(String identifier) {\n\t\ttelegramLoginIdentifier = identifier;\n\t}");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/TelegramLoginPlaceholderFragment.java",
				"TextInputEditText identifier =\n\t\t\t\tv.findViewById(R.id.telegram_login_identifier);");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/TelegramLoginPlaceholderFragment.java",
				"identifier.setText(viewModel.getTelegramLoginIdentifier());");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/TelegramLoginPlaceholderFragment.java",
				"viewModel.setTelegramLoginIdentifier(s.toString());");
		assertFileContains("src/main/res/layout/fragment_telegram_login_placeholder.xml",
				"android:id=\"@+id/telegram_login_identifier\"");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_login_identifier_hint\">Telegram identifier</string>");
	}

	@Test
	public void testTelegramLoginPlaceholderStagesConfirmationStep()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"private final MutableLiveData<Boolean> showingTelegramLoginConfirmation =\n\t\t\tnew MutableLiveData<>(false);");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"void showTelegramLoginPlaceholder() {\n\t\tshowingTelegramLoginConfirmation.setValue(false);\n\t\tstate.setValue(TELEGRAM_LOGIN);\n\t}");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"void showTelegramLoginConfirmation() {\n\t\tshowingTelegramLoginConfirmation.setValue(true);\n\t}");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"void showTelegramLoginIdentifierStep() {\n\t\tshowingTelegramLoginConfirmation.setValue(false);\n\t}");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"boolean isShowingTelegramLoginConfirmation() {\n\t\tBoolean showing = showingTelegramLoginConfirmation.getValue();\n\t\treturn showing != null && showing;\n\t}");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupActivity.java",
				"if (viewModel.isShowingTelegramLoginConfirmation()) {\n\t\t\t\tviewModel.showTelegramLoginIdentifierStep();\n\t\t\t\treturn;\n\t\t\t}");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/TelegramLoginPlaceholderFragment.java",
				"v.findViewById(R.id.btn_telegram_login_continue)\n\t\t\t\t.setOnClickListener(view -> {");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/TelegramLoginPlaceholderFragment.java",
				"viewModel.showTelegramLoginConfirmation();");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/TelegramLoginPlaceholderFragment.java",
				"v.findViewById(R.id.btn_telegram_login_confirmation_back)\n\t\t\t\t.setOnClickListener(view -> {");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/TelegramLoginPlaceholderFragment.java",
				"viewModel.showTelegramLoginIdentifierStep();");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/TelegramLoginPlaceholderFragment.java",
				"confirmationMessage.setText(getString(\n\t\t\t\t\tR.string.telegram_connector_login_confirmation_message,\n\t\t\t\t\tviewModel.getTelegramLoginIdentifier()));");
		assertFileContains("src/main/res/layout/fragment_telegram_login_placeholder.xml",
				"android:id=\"@+id/telegram_login_confirmation\"");
		assertFileContains("src/main/res/layout/fragment_telegram_login_placeholder.xml",
				"android:id=\"@+id/btn_telegram_login_continue\"");
		assertFileContains("src/main/res/layout/fragment_telegram_login_placeholder.xml",
				"android:id=\"@+id/btn_telegram_login_confirmation_back\"");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_login_continue_button\">Continue</string>");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_login_confirmation_message\">Telegram identifier staged for internal Harbor testing: %1$s</string>");
	}

	@Test
	public void testTelegramLoginConfirmationCanReturnToPasswordFlow()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/login/TelegramLoginPlaceholderFragment.java",
				"v.findViewById(R.id.btn_telegram_login_confirmation_continue)\n\t\t\t\t.setOnClickListener(view -> {\n\t\t\t\t\tviewModel.completeTelegramLoginConfirmation();\n\t\t\t\t});");
		assertFileContains("src/main/res/layout/fragment_telegram_login_placeholder.xml",
				"android:id=\"@+id/btn_telegram_login_confirmation_continue\"");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_login_confirmation_continue_button\">Return to Harbor sign-in</string>");
	}

	@Test
	public void testTelegramLoginCompletionStagesLinkedIdentityAfterPasswordSignIn()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"private volatile String pendingTelegramLinkedIdentity = \"\";");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"void completeTelegramLoginConfirmation() {\n\t\tpendingTelegramLinkedIdentity = telegramLoginIdentifier.trim();\n\t\tshowPasswordFragment();\n\t}");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"accountManager.signIn(password);\n\t\t\t\tstorePendingTelegramLinkedIdentity();\n\t\t\t\tpasswordValidated.postEvent(SUCCESS);");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"private void storePendingTelegramLinkedIdentity() {\n\t\tif (pendingTelegramLinkedIdentity.isEmpty()) return;");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"settings.put(\"pref_key_telegram_linked_identity\",");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"settingsManager.mergeSettings(settings, SETTINGS_NAMESPACE);");
	}

	@Test
	public void testStartupActivityShowsTelegramIdentityHandoffConfirmation()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"private final MutableLiveEvent<String> telegramLinkedIdentityStaged =\n\t\t\tnew MutableLiveEvent<>();");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"LiveEvent<String> getTelegramLinkedIdentityStaged() {\n\t\treturn telegramLinkedIdentityStaged;\n\t}");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupViewModel.java",
				"telegramLinkedIdentityStaged.postEvent(pendingTelegramLinkedIdentity);");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupActivity.java",
				"viewModel.getTelegramLinkedIdentityStaged().observeEvent(this,\n\t\t\t\tidentifier -> {\n\t\t\t\t\tstagedTelegramLoginIdentity = identifier;\n\t\t\t\t\tToast.makeText(this,\n\t\t\t\t\t\t\tgetString(\n\t\t\t\t\t\t\t\t\tR.string.telegram_connector_login_handoff_staged,\n\t\t\t\t\t\t\t\t\tidentifier),\n\t\t\t\t\t\t\tLENGTH_LONG).show();\n\t\t\t\t});");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_login_handoff_staged\">Telegram identity staged for Harbor internal testing: %1$s</string>");
	}

	@Test
	public void testStartupActivityPersistsTelegramIdentityHandoffAcrossRecreation()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupActivity.java",
				"private static final String KEY_STAGED_TELEGRAM_LOGIN_IDENTITY =\n\t\t\t\"stagedTelegramLoginIdentity\";");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupActivity.java",
				"if (state != null) {\n\t\t\tstagedTelegramLoginIdentity = state.getString(\n\t\t\t\t\tKEY_STAGED_TELEGRAM_LOGIN_IDENTITY, \"\");\n\t\t}");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupActivity.java",
				"protected void onSaveInstanceState(Bundle state) {\n\t\tsuper.onSaveInstanceState(state);\n\t\tstate.putString(KEY_STAGED_TELEGRAM_LOGIN_IDENTITY,\n\t\t\t\tstagedTelegramLoginIdentity);\n\t}");
	}

	@Test
	public void testStartupLoginCanOfferTelegramSetupEntrypointAfterFreshHandoff()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupActivity.java",
				"public static final String EXTRA_STAGED_TELEGRAM_LOGIN_IDENTITY =\n\t\t\t\"briar.STAGED_TELEGRAM_LOGIN_IDENTITY\";");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupActivity.java",
				"private String stagedTelegramLoginIdentity = \"\";");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupActivity.java",
				"stagedTelegramLoginIdentity = identifier;");
		assertFileContains("src/main/java/org/briarproject/briar/android/login/StartupActivity.java",
				"result.putExtra(EXTRA_STAGED_TELEGRAM_LOGIN_IDENTITY,\n\t\t\t\t\t\tstagedTelegramLoginIdentity);");
		assertFileContains("src/main/java/org/briarproject/briar/android/activity/BriarActivity.java",
				"private static final String EXTRA_PENDING_TELEGRAM_LOGIN_ENTRYPOINT =\n\t\t\t\"briar.PENDING_TELEGRAM_LOGIN_ENTRYPOINT\";");
		assertFileContains("src/main/java/org/briarproject/briar/android/activity/BriarActivity.java",
				"String stagedIdentity = data.getStringExtra(\n\t\t\t\t\t\tStartupActivity.EXTRA_STAGED_TELEGRAM_LOGIN_IDENTITY);");
		assertFileContains("src/main/java/org/briarproject/briar/android/activity/BriarActivity.java",
				"getIntent().putExtra(EXTRA_PENDING_TELEGRAM_LOGIN_ENTRYPOINT,\n\t\t\t\t\t\t\tstagedIdentity);");
		assertFileContains("src/main/java/org/briarproject/briar/android/activity/BriarActivity.java",
				"maybeShowTelegramLoginSetupEntryPoint();");
		assertFileContains("src/main/java/org/briarproject/briar/android/activity/BriarActivity.java",
				"private void maybeShowTelegramLoginSetupEntryPoint() {");
		assertFileContains("src/main/java/org/briarproject/briar/android/activity/BriarActivity.java",
				"getIntent().removeExtra(EXTRA_PENDING_TELEGRAM_LOGIN_ENTRYPOINT);");
		assertFileContains("src/main/java/org/briarproject/briar/android/activity/BriarActivity.java",
				"R.string.telegram_connector_login_entrypoint_message,\n\t\t\t\t\t\tlinkedIdentity))");
		assertFileContains("src/main/java/org/briarproject/briar/android/activity/BriarActivity.java",
				"Intent i = new Intent(this, SettingsActivity.class);");
		assertFileContains("src/main/java/org/briarproject/briar/android/activity/BriarActivity.java",
				"i.setAction(ACTION_MANAGE_NETWORK_USAGE);");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_login_entrypoint_message\">Telegram identity staged for Harbor internal testing: %1$s\\n\\nOpen Telegram setup to continue the placeholder flow.</string>");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_login_entrypoint_continue_button\">Open Telegram setup</string>");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_login_entrypoint_cancel_button\">Stay in Harbor</string>");
	}

	@Test
	public void testPostSignInTelegramSetupEntrypointCanAutoOpenPlaceholder()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/SettingsActivity.java",
				"static final String EXTRA_OPEN_TELEGRAM_SETUP = \"openTelegramSetup\";");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/SettingsActivity.java",
				"boolean consumeOpenTelegramSetup() {\n\t\tboolean openTelegramSetup = getIntent().getBooleanExtra(\n\t\t\t\tEXTRA_OPEN_TELEGRAM_SETUP, false);\n\t\tgetIntent().removeExtra(EXTRA_OPEN_TELEGRAM_SETUP);\n\t\treturn openTelegramSetup;\n\t}");
		assertFileContains("src/main/java/org/briarproject/briar/android/activity/BriarActivity.java",
				"i.putExtra(SettingsActivity.EXTRA_OPEN_TELEGRAM_SETUP, true);");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"if (requireSettingsActivity().consumeOpenTelegramSetup()) {");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"showTelegramSetupDialog(requireSettingsActivity()");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				".isTelegramConnectorReady(), value);");
	}

	@Test
	public void testConnectionsSettingsExposeTelegramSetupPlaceholder()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"telegramStatus.setSelectable(telegramConnector.isEnabled());");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"telegramStatus.setOnPreferenceClickListener(preference -> {");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"showTelegramSetupDialog(requireSettingsActivity()\n\t\t\t\t\t.isTelegramConnectorReady(),\n\t\t\t\t\ttelegramLinkedIdentity.getText());");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"private void showTelegramSetupDialog(boolean ready,\n\t\t\t@Nullable String linkedIdentity) {");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_setup_ready_message\">Telegram account linking is the next Harbor internal test step. Connector syncing is still inactive in this build.</string>");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_setup_unavailable_message\">Sign into Harbor and finish app startup before Telegram account linking can be tested. Connector syncing is still inactive in this build.</string>");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_setup_configured_message\">Telegram account %1$s is stored for internal Harbor linking tests. Connector syncing is still inactive in this build.</string>");
	}

	@Test
	public void testTelegramSetupPlaceholderCanContinueToIdentityReview()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"builder.setPositiveButton(ready\n\t\t\t\t? R.string.telegram_connector_setup_continue_button\n\t\t\t\t: R.string.ok,");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"if (ready) showTelegramIdentityEditor();");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"builder.setNegativeButton(R.string.cancel, null);");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"private void showTelegramIdentityEditor() {\n\t\ttelegramLinkedIdentity.performClick();\n\t}");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_setup_continue_button\">Review Telegram identity</string>");
	}

	@Test
	public void testTelegramIdentityReviewCanContinueToVerificationPlaceholder()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"static final String PREF_KEY_TELEGRAM_VERIFICATION = \"pref_key_telegram_verification\";");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"private Preference telegramVerification;");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"telegramVerification = findPreference(PREF_KEY_TELEGRAM_VERIFICATION);");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"telegramVerification.setVisible(telegramConnector.isEnabled());");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"telegramVerification.setOnPreferenceClickListener(preference -> {");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"updateTelegramVerificationState(telegramLinkedIdentity.getText());");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"showTelegramVerificationDialog(telegramLinkedIdentity.getText());");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"telegramVerification.setEnabled(requireSettingsActivity().isTelegramConnectorReady()\n\t\t\t\t\t\t\t&& !isNullOrEmpty(value));");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"private void updateTelegramVerificationState(@Nullable String linkedIdentity) {");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"private void showTelegramVerificationDialog(@Nullable String linkedIdentity) {");
		assertFileContains("src/main/res/xml/settings_connections.xml",
				"android:key=\"pref_key_telegram_verification\"");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_verification_title\">Telegram verification</string>");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_verification_ready_summary\">Continue internal Harbor verification for Telegram account %1$s.</string>");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_verification_dialog_message\">Telegram verification for %1$s stays in Harbor local-only placeholder flow. Real Telegram authentication and syncing are still inactive in this build.</string>");
	}

	@Test
	public void testTelegramVerificationPlaceholderCanContinueToAuthenticationPlaceholder()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				".setPositiveButton(\n\t\t\t\t\t\tR.string.telegram_connector_verification_continue_button,");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"showTelegramAuthenticationPlaceholder(linkedIdentity))");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				".setNegativeButton(R.string.cancel, null)");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"private void showTelegramAuthenticationPlaceholder(String linkedIdentity) {");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_verification_continue_button\">Open authentication placeholder</string>");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_auth_placeholder_title\">Telegram authentication</string>");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_auth_placeholder_message\">Telegram authentication for %1$s remains a Harbor local-only placeholder. Real Telegram sign-in and syncing are still inactive in this build.</string>");
	}

	@Test
	public void testTelegramAuthPlaceholderCompletionUpdatesVerificationSummary()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"private String telegramAuthenticationPlaceholderCompletedIdentity;");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"telegramAuthenticationPlaceholderCompletedIdentity = linkedIdentity;");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"updateTelegramVerificationState(linkedIdentity);");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"linkedIdentity.equals(telegramAuthenticationPlaceholderCompletedIdentity)");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_verification_completed_summary\">Authentication placeholder reviewed for Telegram account %1$s.</string>");
	}

	@Test
	public void testCompletedVerificationDialogMentionsPreviouslyReviewedPlaceholder()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"boolean alreadyReviewed = hasCompletedTelegramVerification(linkedIdentity);");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"R.string.telegram_connector_verification_dialog_completed_message");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"private boolean hasCompletedTelegramVerification(\n\t\t\t@Nullable String linkedIdentity) {");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_verification_dialog_completed_message\">Telegram verification for %1$s is reopening the Harbor local-only placeholder flow after the authentication placeholder was already reviewed. Real Telegram authentication and syncing are still inactive in this build.</string>");
	}

	@Test
	public void testTelegramVerificationCompletionResetsWhenIdentityChanges()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"clearTelegramVerificationCompletionIfIdentityChanged(value);");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"private void clearTelegramVerificationCompletionIfIdentityChanged(\n\t\t\t@Nullable String linkedIdentity) {");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"if (isNullOrEmpty(telegramAuthenticationPlaceholderCompletedIdentity)) return;");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"if (!telegramAuthenticationPlaceholderCompletedIdentity.equals(linkedIdentity)) {");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"telegramAuthenticationPlaceholderCompletedIdentity = null;");
	}

	@Test
	public void testConnectionsSettingsExposeTelegramIdentityLinkingSeam()
			throws IOException {
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"static final String PREF_KEY_TELEGRAM_LINKED_IDENTITY =\n\t\t\t\"pref_key_telegram_linked_identity\";");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"telegramLinkedIdentity.setPreferenceDataStore(viewModel.settingsStore);");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"telegramLinkedIdentity.setSummaryProvider(preference -> {");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"viewModel.getTelegramLinkedIdentity().observe(lifecycleOwner,\n\t\t\t\tvalue -> {");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/ConnectionsFragment.java",
				"telegramLinkedIdentity.setText(value);");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/SettingsViewModel.java",
				"private final MutableLiveData<String> telegramLinkedIdentity =\n\t\t\tnew MutableLiveData<>();");
		assertFileContains("src/main/java/org/briarproject/briar/android/settings/SettingsViewModel.java",
				"telegramLinkedIdentity.postValue(settings.get(\n\t\t\t\tConnectionsFragment.PREF_KEY_TELEGRAM_LINKED_IDENTITY));");
		assertFileContains("src/main/res/xml/settings_connections.xml",
				"android:key=\"pref_key_telegram_linked_identity\"");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_identity_title\">Telegram account</string>");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"telegram_connector_identity_empty_summary\">No Telegram account linked yet.</string>");
	}

	private static void assertFileContains(String moduleRelativePath,
			String expectedText) throws IOException {
		String contents = new String(
				Files.readAllBytes(resolveModulePath(moduleRelativePath)),
				StandardCharsets.UTF_8);
		assertTrue("Expected to find '" + expectedText + "' in "
				+ moduleRelativePath, contents.contains(expectedText));
	}

	private static void assertFileNotContains(String moduleRelativePath,
			String unexpectedText) throws IOException {
		String contents = new String(
				Files.readAllBytes(resolveModulePath(moduleRelativePath)),
				StandardCharsets.UTF_8);
		assertFalse("Expected not to find '" + unexpectedText + "' in "
				+ moduleRelativePath, contents.contains(unexpectedText));
	}

	private static Path resolveModulePath(String moduleRelativePath) {
		Path cwd = Paths.get("").toAbsolutePath().normalize();
		Path direct = cwd.resolve(moduleRelativePath).normalize();
		if (Files.exists(direct)) return direct;

		Path nested = cwd.resolve("briar-android").resolve(moduleRelativePath)
				.normalize();
		if (Files.exists(nested)) return nested;

		throw new IllegalStateException("Could not resolve module path: "
				+ moduleRelativePath + " from " + cwd);
	}
}
