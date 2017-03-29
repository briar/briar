package org.briarproject.briar.android.login;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.design.widget.TextInputLayout;
import android.widget.Button;
import android.widget.EditText;

import org.briarproject.briar.BuildConfig;
import org.briarproject.briar.R;
import org.briarproject.briar.android.TestBriarApplication;
import org.briarproject.briar.android.controller.handler.ResultHandler;
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

import static junit.framework.Assert.assertEquals;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.NONE;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.QUITE_STRONG;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.QUITE_WEAK;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.STRONG;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.WEAK;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21,
		application = TestBriarApplication.class)
public class ChangePasswordActivityTest {

	private static final int TIMEOUT_MS = 10 * 1000;

	private TestChangePasswordActivity changePasswordActivity;
	private TextInputLayout passwordConfirmationWrapper;
	private EditText currentPassword;
	private EditText newPassword;
	private EditText newPasswordConfirmation;
	private StrengthMeter strengthMeter;
	private Button changePasswordButton;

	@Mock
	private PasswordController passwordController;
	@Mock
	private SetupController setupController;
	@Captor
	private ArgumentCaptor<ResultHandler<Boolean>> resultCaptor;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		changePasswordActivity =
				Robolectric.setupActivity(TestChangePasswordActivity.class);
		passwordConfirmationWrapper = (TextInputLayout) changePasswordActivity
				.findViewById(R.id.new_password_confirm_wrapper);
		currentPassword = (EditText) changePasswordActivity
				.findViewById(R.id.current_password_entry);
		newPassword = (EditText) changePasswordActivity
				.findViewById(R.id.new_password_entry);
		newPasswordConfirmation = (EditText) changePasswordActivity
				.findViewById(R.id.new_password_confirm);
		strengthMeter = (StrengthMeter) changePasswordActivity
				.findViewById(R.id.strength_meter);
		changePasswordButton = (Button) changePasswordActivity
				.findViewById(R.id.change_password);
	}

	private void testStrengthMeter(String pass, float strength, int color) {
		newPassword.setText(pass);
		assertEquals(strengthMeter.getProgress(),
				(int) (strengthMeter.getMax() * strength));
		assertEquals(color, strengthMeter.getColor());
	}

	@Test
	public void testPasswordMatchUI() {
		// Password mismatch
		newPassword.setText("really.safe.password");
		newPasswordConfirmation.setText("really.safe.pass");
		assertEquals(changePasswordButton.isEnabled(), false);
		assertEquals(passwordConfirmationWrapper.getError(),
				changePasswordActivity
						.getString(R.string.passwords_do_not_match));
		// Button enabled
		newPassword.setText("really.safe.pass");
		newPasswordConfirmation.setText("really.safe.pass");
		// Confirm that the password mismatch error message is not visible
		Assert.assertNotEquals(passwordConfirmationWrapper.getError(),
				changePasswordActivity
						.getString(R.string.passwords_do_not_match));
		// Nick has not been set, expect the button to be disabled
		assertEquals(changePasswordButton.isEnabled(), false);
	}

	@Test
	public void testChangePasswordUI() {
		PasswordController mockedPasswordController = this.passwordController;
		SetupController mockedSetupController = this.setupController;
		changePasswordActivity.setPasswordController(mockedPasswordController);
		changePasswordActivity.setSetupController(mockedSetupController);
		// Mock strong password strength answer
		when(mockedSetupController.estimatePasswordStrength(anyString()))
				.thenReturn(STRONG);
		String curPass = "old.password";
		String safePass = "really.safe.password";
		currentPassword.setText(curPass);
		newPassword.setText(safePass);
		newPasswordConfirmation.setText(safePass);
		// Confirm that the create account button is clickable
		assertEquals(changePasswordButton.isEnabled(), true);
		changePasswordButton.performClick();
		// Verify that the controller's method was called with the correct
		// params and get the callback
		verify(mockedPasswordController, times(1))
				.changePassword(eq(curPass), eq(safePass),
						resultCaptor.capture());
		// execute the callbacks
		resultCaptor.getValue().onResult(true);
		assertEquals(changePasswordActivity.isFinishing(), true);
	}

	@Test
	public void testPasswordChange() {
		PasswordController passwordController =
				changePasswordActivity.getPasswordController();
		SetupController setupController =
				changePasswordActivity.getSetupController();
		// mock a resulthandler
		ResultHandler<Void> resultHandler =
				(ResultHandler<Void>) mock(ResultHandler.class);
		setupController.storeAuthorInfo("nick", "some.old.pass", resultHandler);
		// blocking verification call with timeout that waits until the mocked
		// result gets called with handle 0L, the expected value
		verify(resultHandler, timeout(TIMEOUT_MS).times(1)).onResult(null);
		SharedPreferences prefs = changePasswordActivity
				.getSharedPreferences("db", Context.MODE_PRIVATE);
		// Confirm database key
		assertTrue(prefs.contains("key"));
		String oldKey = prefs.getString("key", null);
		// mock a resulthandler
		ResultHandler<Boolean> resultHandler2 =
				(ResultHandler<Boolean>) mock(ResultHandler.class);
		passwordController.changePassword("some.old.pass", "some.strong.pass",
				resultHandler2);
		// blocking verification call with timeout that waits until the mocked
		// result gets called with handle 0L, the expected value
		verify(resultHandler2, timeout(TIMEOUT_MS).times(1)).onResult(true);
		// Confirm database key
		assertTrue(prefs.contains("key"));
		assertNotEquals(oldKey, prefs.getString("key", null));
		// Note that Robolectric uses its own persistant storage that it
		// wipes clean after each test run, no need to clean up manually.
	}

	@Test
	public void testStrengthMeter() {
		SetupController controller =
				changePasswordActivity.getSetupController();

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
		Assert.assertNotNull(changePasswordActivity);
		// replace the setup controller with our mocked copy
		SetupController mockedController = this.setupController;
		changePasswordActivity.setSetupController(mockedController);
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
