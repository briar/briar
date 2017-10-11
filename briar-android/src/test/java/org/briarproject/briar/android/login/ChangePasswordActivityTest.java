package org.briarproject.briar.android.login;

import android.support.design.widget.TextInputLayout;
import android.widget.Button;
import android.widget.EditText;

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
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.NONE;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.QUITE_STRONG;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.QUITE_WEAK;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.STRONG;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.WEAK;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, application = TestBriarApplication.class,
		packageName = "org.briarproject.briar")
public class ChangePasswordActivityTest {

	private TestChangePasswordActivity changePasswordActivity;
	private TextInputLayout passwordConfirmationWrapper;
	private EditText currentPassword;
	private EditText newPassword;
	private EditText newPasswordConfirmation;
	private StrengthMeter strengthMeter;
	private Button changePasswordButton;

	@Mock
	private PasswordController passwordController;
	@Captor
	private ArgumentCaptor<ResultHandler<Boolean>> resultCaptor;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		changePasswordActivity =
				Robolectric.setupActivity(TestChangePasswordActivity.class);
		passwordConfirmationWrapper = changePasswordActivity
				.findViewById(R.id.new_password_confirm_wrapper);
		currentPassword = changePasswordActivity
				.findViewById(R.id.current_password_entry);
		newPassword = changePasswordActivity
				.findViewById(R.id.new_password_entry);
		newPasswordConfirmation = changePasswordActivity
				.findViewById(R.id.new_password_confirm);
		strengthMeter = changePasswordActivity
				.findViewById(R.id.strength_meter);
		changePasswordButton = changePasswordActivity
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
		changePasswordActivity.setPasswordController(passwordController);
		// Mock strong password strength answer
		when(passwordController.estimatePasswordStrength(anyString()))
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
		verify(passwordController, times(1))
				.changePassword(eq(curPass), eq(safePass),
						resultCaptor.capture());
		// execute the callbacks
		resultCaptor.getValue().onResult(true);
		assertEquals(changePasswordActivity.isFinishing(), true);
	}

	@Test
	public void testStrengthMeterUI() {
		Assert.assertNotNull(changePasswordActivity);
		// replace the password controller with our mocked copy
		changePasswordActivity.setPasswordController(passwordController);
		// Mock answers for UI testing only
		when(passwordController.estimatePasswordStrength("strong")).thenReturn(
				STRONG);
		when(passwordController.estimatePasswordStrength("qstrong")).thenReturn(
				QUITE_STRONG);
		when(passwordController.estimatePasswordStrength("qweak")).thenReturn(
				QUITE_WEAK);
		when(passwordController.estimatePasswordStrength("weak")).thenReturn(
				WEAK);
		when(passwordController.estimatePasswordStrength("empty")).thenReturn(
				NONE);
		// Test the meters progress and color for several values
		testStrengthMeter("strong", STRONG, StrengthMeter.GREEN);
		Mockito.verify(passwordController, Mockito.times(1))
				.estimatePasswordStrength(eq("strong"));
		testStrengthMeter("qstrong", QUITE_STRONG, StrengthMeter.LIME);
		Mockito.verify(passwordController, Mockito.times(1))
				.estimatePasswordStrength(eq("qstrong"));
		testStrengthMeter("qweak", QUITE_WEAK, StrengthMeter.YELLOW);
		Mockito.verify(passwordController, Mockito.times(1))
				.estimatePasswordStrength(eq("qweak"));
		testStrengthMeter("weak", WEAK, StrengthMeter.ORANGE);
		Mockito.verify(passwordController, Mockito.times(1))
				.estimatePasswordStrength(eq("weak"));
		// Not sure this should be the correct behaviour on an empty input ?
		testStrengthMeter("empty", NONE, StrengthMeter.RED);
		Mockito.verify(passwordController, Mockito.times(1))
				.estimatePasswordStrength(eq("empty"));
	}

}
