package org.briarproject.briar.android.login;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.design.widget.TextInputLayout;
import android.widget.Button;
import android.widget.EditText;

import com.google.common.base.Strings;

import org.briarproject.bramble.api.identity.AuthorConstants;
import org.briarproject.briar.BuildConfig;
import org.briarproject.briar.R;
import org.briarproject.briar.android.TestBriarApplication;
import org.briarproject.briar.android.controller.handler.ResultHandler;
import org.briarproject.briar.android.navdrawer.NavDrawerActivity;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import static junit.framework.Assert.assertEquals;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.NONE;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.QUITE_STRONG;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.QUITE_WEAK;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.STRONG;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.WEAK;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21,
		application = TestBriarApplication.class)
public class SetupActivityTest {

	private static final int TIMEOUT_MS = 10 * 1000;

	private TestSetupActivity setupActivity;
	private TextInputLayout nicknameEntryWrapper;
	private TextInputLayout passwordConfirmationWrapper;
	private EditText nicknameEntry;
	private EditText passwordEntry;
	private EditText passwordConfirmation;
	private StrengthMeter strengthMeter;
	private Button createAccountButton;

	@Mock
	private SetupController setupController;
	@Captor
	private ArgumentCaptor<ResultHandler<Void>> authorCaptor;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		setupActivity = Robolectric.setupActivity(TestSetupActivity.class);
		nicknameEntryWrapper = (TextInputLayout) setupActivity
				.findViewById(R.id.nickname_entry_wrapper);
		passwordConfirmationWrapper = (TextInputLayout) setupActivity
				.findViewById(R.id.password_confirm_wrapper);
		nicknameEntry =
				(EditText) setupActivity.findViewById(R.id.nickname_entry);
		passwordEntry =
				(EditText) setupActivity.findViewById(R.id.password_entry);
		passwordConfirmation =
				(EditText) setupActivity.findViewById(R.id.password_confirm);
		strengthMeter =
				(StrengthMeter) setupActivity.findViewById(R.id.strength_meter);
		createAccountButton =
				(Button) setupActivity.findViewById(R.id.create_account);
	}

	private void testStrengthMeter(String pass, float strength, int color) {
		passwordEntry.setText(pass);
		assertEquals(strengthMeter.getProgress(),
				(int) (strengthMeter.getMax() * strength));
		assertEquals(color, strengthMeter.getColor());
	}

	@Test
	public void testPasswordMatchUI() {
		// Password mismatch
		passwordEntry.setText("really.safe.password");
		passwordConfirmation.setText("really.safe.pass");
		assertEquals(createAccountButton.isEnabled(), false);
		assertEquals(passwordConfirmationWrapper.getError(),
				setupActivity.getString(R.string.passwords_do_not_match));
		// Button enabled
		passwordEntry.setText("really.safe.pass");
		passwordConfirmation.setText("really.safe.pass");
		// Confirm that the password mismatch error message is not visible
		Assert.assertNotEquals(passwordConfirmationWrapper.getError(),
				setupActivity.getString(R.string.passwords_do_not_match));
		// Nick has not been set, expect the button to be disabled
		assertEquals(createAccountButton.isEnabled(), false);
	}

	@Test
	public void testCreateAccountUI() {
		SetupController mockedController = this.setupController;
		setupActivity.setController(mockedController);
		// Mock strong password strength answer
		when(mockedController.estimatePasswordStrength(anyString())).thenReturn(
				STRONG);
		String safePass = "really.safe.password";
		String nick = "nick.nickerton";
		passwordEntry.setText(safePass);
		passwordConfirmation.setText(safePass);
		nicknameEntry.setText(nick);
		// Confirm that the create account button is clickable
		assertEquals(createAccountButton.isEnabled(), true);
		createAccountButton.performClick();
		// Verify that the controller's method was called with the correct
		// params and get the callback
		verify(mockedController, times(1))
				.storeAuthorInfo(eq(nick), eq(safePass),
						authorCaptor.capture());
		authorCaptor.getValue().onResult(null);
		// execute the callback
		assertEquals(setupActivity.isFinishing(), true);
		// Confirm that the correct Activity has been started
		ShadowActivity shadowActivity = shadowOf(setupActivity);
		Intent intent = shadowActivity.peekNextStartedActivity();
		assertEquals(intent.getComponent().getClassName(),
				NavDrawerActivity.class.getName());
	}

	@Test
	public void testNicknameUI() {
		Assert.assertNotNull(setupActivity);
		String longNick =
				Strings.padEnd("*", AuthorConstants.MAX_AUTHOR_NAME_LENGTH + 1,
						'*');
		nicknameEntry.setText(longNick);
		// Nickname should be too long
		assertEquals(nicknameEntryWrapper.getError(),
				setupActivity.getString(R.string.name_too_long));
	}

	@Test
	public void testAccountCreation() {
		SetupController controller = setupActivity.getController();
		// mock a resulthandler
		ResultHandler<Void> resultHandler =
				(ResultHandler<Void>) mock(ResultHandler.class);
		controller.storeAuthorInfo("nick", "some.strong.pass", resultHandler);
		// blocking verification call with timeout that waits until the mocked
		// result gets called with handle 0L, the expected value
		verify(resultHandler, timeout(TIMEOUT_MS).times(1)).onResult(null);
		SharedPreferences prefs =
				setupActivity.getSharedPreferences("db", Context.MODE_PRIVATE);
		// Confirm database key
		assertTrue(prefs.contains("key"));
		// Note that Robolectric uses its own persistant storage that it
		// wipes clean after each test run, no need to clean up manually.
	}

	@Test
	public void testStrengthMeter() {
		SetupController controller = setupActivity.getController();

		String strongPass = "very.strong.password.123";
		String weakPass = "we";
		String quiteStrongPass = "quite.strong";

		float val = controller.estimatePasswordStrength(strongPass);
		assertTrue(val == STRONG);
		val = controller.estimatePasswordStrength(weakPass);
		assertTrue(val < WEAK && val > NONE);
		val = controller.estimatePasswordStrength(quiteStrongPass);
		assertTrue(val < STRONG && val > QUITE_WEAK);
	}

	@Test
	public void testStrengthMeterUI() {
		Assert.assertNotNull(setupActivity);
		// replace the setup controller with our mocked copy
		SetupController mockedController = this.setupController;
		setupActivity.setController(mockedController);
		// Mock answers for UI testing only
		when(mockedController.estimatePasswordStrength("strong")).thenReturn(
				STRONG);
		when(mockedController.estimatePasswordStrength("qstrong")).thenReturn(
				QUITE_STRONG);
		when(mockedController.estimatePasswordStrength("qweak")).thenReturn(
				QUITE_WEAK);
		when(mockedController.estimatePasswordStrength("weak")).thenReturn(
				WEAK);
		when(mockedController.estimatePasswordStrength("empty")).thenReturn(
				NONE);
		// Test the meters progress and color for several values
		testStrengthMeter("strong", STRONG, StrengthMeter.GREEN);
		Mockito.verify(mockedController, Mockito.times(1))
				.estimatePasswordStrength(eq("strong"));
		testStrengthMeter("qstrong", QUITE_STRONG, StrengthMeter.LIME);
		Mockito.verify(mockedController, Mockito.times(1))
				.estimatePasswordStrength(eq("qstrong"));
		testStrengthMeter("qweak", QUITE_WEAK, StrengthMeter.YELLOW);
		Mockito.verify(mockedController, Mockito.times(1))
				.estimatePasswordStrength(eq("qweak"));
		testStrengthMeter("weak", WEAK, StrengthMeter.ORANGE);
		Mockito.verify(mockedController, Mockito.times(1))
				.estimatePasswordStrength(eq("weak"));
		// Not sure this should be the correct behaviour on an empty input ?
		testStrengthMeter("empty", NONE, StrengthMeter.RED);
		Mockito.verify(mockedController, Mockito.times(1))
				.estimatePasswordStrength(eq("empty"));
	}
}
