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
