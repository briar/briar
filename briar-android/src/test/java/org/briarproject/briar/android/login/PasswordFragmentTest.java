package org.briarproject.briar.android.login;

import android.support.design.widget.TextInputLayout;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import org.briarproject.briar.R;
import org.briarproject.briar.android.TestBriarApplication;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.NONE;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.QUITE_STRONG;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.QUITE_WEAK;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.STRONG;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.WEAK;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.shadows.support.v4.SupportFragmentTestUtil.startFragment;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, application = TestBriarApplication.class,
		packageName = "org.briarproject.briar")
public class PasswordFragmentTest {

	private PasswordFragment passwordFragment = new PasswordFragment();
	private EditText passwordEntry;
	private EditText passwordConfirmation;
	private TextInputLayout passwordConfirmationWrapper;
	private StrengthMeter strengthMeter;
	private Button createAccountButton;

	@Mock
	private SetupController setupController;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		startFragment(passwordFragment, SetupActivity.class);

		View v = passwordFragment.getView();
		passwordEntry = v.findViewById(R.id.password_entry);
		passwordConfirmation = v.findViewById(R.id.password_confirm);
		passwordConfirmationWrapper =
				v.findViewById(R.id.password_confirm_wrapper);
		strengthMeter = v.findViewById(R.id.strength_meter);
		createAccountButton = v.findViewById(R.id.next);
	}

	@Test
	public void testCreateAccountUI() {
		String safePass = "really.safe.password";

		passwordFragment.setupController = setupController;
		when(setupController.needToShowDozeFragment()).thenReturn(false);
		when(setupController.estimatePasswordStrength(safePass))
				.thenReturn(STRONG);

		passwordEntry.setText(safePass);
		passwordConfirmation.setText(safePass);
		// Confirm that the create account button is clickable
		assertEquals(createAccountButton.isEnabled(), true);
		createAccountButton.performClick();

		// assert controller has been called properly
		verify(setupController, times(1)).setPassword(safePass);
		verify(setupController, times(1)).showDozeOrCreateAccount();
	}

	@Test
	public void testStrengthMeterUI() {
		// Test the meters' progress and color for several values
		testStrengthMeter("1234567890ab", STRONG, StrengthMeter.GREEN);
		testStrengthMeter("123456789", QUITE_STRONG, StrengthMeter.LIME);
		testStrengthMeter("123456", QUITE_WEAK, StrengthMeter.YELLOW);
		testStrengthMeter("123", WEAK, StrengthMeter.ORANGE);
		testStrengthMeter("", NONE, StrengthMeter.RED);
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
				passwordFragment.getString(R.string.passwords_do_not_match));
		// Button enabled
		passwordEntry.setText("really.safe.pass");
		passwordConfirmation.setText("really.safe.pass");
		// Confirm that the password mismatch error message is not visible
		assertNotEquals(passwordConfirmationWrapper.getError(),
				passwordFragment.getString(R.string.passwords_do_not_match));
		// Passwords match, so button should be enabled
		assertEquals(createAccountButton.isEnabled(), true);
	}

}
