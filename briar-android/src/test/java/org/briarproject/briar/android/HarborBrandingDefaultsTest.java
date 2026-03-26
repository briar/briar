package org.briarproject.briar.android;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static junit.framework.Assert.assertTrue;

public class HarborBrandingDefaultsTest {

	@Test
	public void testReleaseAppNameIsHarbor() throws IOException {
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"app_name\" translatable=\"false\">Harbor</string>");
	}

	@Test
	public void testCoreSetupAndSignInCopyUsesHarbor() throws IOException {
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"setup_title\">Welcome to Harbor</string>");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"sign_in_title\">Sign into Harbor</string>");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"reminder_notification_title\">Signed out of Harbor</string>");
	}

	@Test
	public void testSplashAndSignInBrandingUseAppNameText() throws IOException {
		assertFileContains("src/main/res/layout/fragment_password.xml",
				"android:text=\"@string/app_name\"");
		assertFileContains("src/main/res/layout/fragment_password.xml",
				"@drawable/navigation_drawer_mark");
		assertFileContains("src/main/res/layout/splash.xml",
				"android:text=\"@string/app_name\"");
		assertFileContains("src/main/res/layout/splash.xml",
				"@drawable/navigation_drawer_mark");
	}

	@Test
	public void testDebugAppNameIsHarborDebug() throws IOException {
		assertFileContains("src/debug/res/values/strings.xml",
				"<string name=\"app_name\" translatable=\"false\">Harbor Debug</string>");
	}

	@Test
	public void testDisplaySettingsDefaultToDarkTheme() throws IOException {
		assertFileContains("src/main/res/xml/settings_display.xml",
				"android:defaultValue=\"@string/pref_theme_dark_value\"");
	}

	@Test
	public void testApplicationStartupDefaultsToDarkTheme() throws IOException {
		assertFileContains(
				"src/main/java/org/briarproject/briar/android/BriarApplicationImpl.java",
				"theme = getString(R.string.pref_theme_dark_value);");
	}

	@Test
	public void testAccountResetReturnsToDarkTheme() throws IOException {
		assertFileContains(
				"src/main/java/org/briarproject/bramble/account/BriarAccountManager.java",
				"appContext.getString(R.string.pref_theme_dark_value)");
	}

	@Test
	public void testOfflineShareCopyUsesHarbor() throws IOException {
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"share_app_link_text\">Download Harbor at %s</string>");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"hotspot_intro\">Share this app with someone nearby without an Internet connection by using your phone\\'s Wi-Fi.");
		assertFileContains("src/main/res/values/strings.xml",
				"download the Harbor app from your phone.</string>");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"hotspot_notification_title\">Sharing Harbor offline</string>");
		assertFileContains("src/main/res/values/strings.xml",
				"People who want to download Harbor can connect to the hotspot");
		assertFileContains("src/main/res/values/strings.xml",
				"People who are connected to the hotspot can download Harbor");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"website_download_title_1\">Download Harbor %s</string>");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"website_download_intro_1\">Someone nearby shared Harbor with you.</string>");
		assertFileContains("src/main/res/values/strings.xml",
				"<string name=\"website_download_button\">Download Harbor</string>");
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
		Path direct = cwd.resolve(moduleRelativePath);
		if (Files.exists(direct)) return direct;

		Path nested = cwd.resolve("briar-android").resolve(moduleRelativePath);
		if (Files.exists(nested)) return nested;

		throw new IllegalStateException("Could not resolve module path: "
				+ moduleRelativePath + " from " + cwd);
	}
}
