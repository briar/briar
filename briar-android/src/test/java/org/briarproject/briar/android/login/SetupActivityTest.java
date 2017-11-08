package org.briarproject.briar.android.login;

import android.content.Context;
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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21,
		application = TestBriarApplication.class,
		packageName = "org.briarproject.briar")
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

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		setupActivity = Robolectric.setupActivity(TestSetupActivity.class);
		nicknameEntryWrapper = (TextInputLayout) setupActivity
				.findViewById(R.id.nickname_entry_wrapper);
		nicknameEntry =
				(EditText) setupActivity.findViewById(R.id.nickname_entry);
		createAccountButton = (Button) setupActivity.findViewById(R.id.next);
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
	public void testPasswordMatchUI() {
		proceedToPasswordFragment();
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
		// Passwords match, so button should be enabled
		assertEquals(createAccountButton.isEnabled(), true);
	}

	@Test
	public void testAccountCreation() {
		SetupController controller = setupActivity.getController();
		controller.setAuthorName("nick");
		controller.setPassword("password");
		// mock a resulthandler
		ResultHandler<Void> resultHandler = mock(ResultHandler.class);
		controller.createAccount(resultHandler);
		verify(resultHandler, timeout(TIMEOUT_MS).times(1)).onResult(null);
		SharedPreferences prefs =
				setupActivity.getSharedPreferences("db", Context.MODE_PRIVATE);
		// Confirm database key
		assertTrue(prefs.contains("key"));
		// Note that Robolectric uses its own persistent storage that it
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
		proceedToPasswordFragment();
		// Test the meters progress and color for several values
		testStrengthMeter("1234567890ab", STRONG, StrengthMeter.GREEN);
		testStrengthMeter("123456789", QUITE_STRONG, StrengthMeter.LIME);
		testStrengthMeter("123456", QUITE_WEAK, StrengthMeter.YELLOW);
		testStrengthMeter("123", WEAK, StrengthMeter.ORANGE);
		testStrengthMeter("", NONE, StrengthMeter.RED);
	}

	private void proceedToPasswordFragment() {
		// proceed to password fragment
		nicknameEntry.setText("nick");
		createAccountButton.performClick();

		// find UI elements in new fragment
		strengthMeter =
				(StrengthMeter) setupActivity.findViewById(R.id.strength_meter);
		passwordConfirmationWrapper = (TextInputLayout) setupActivity
				.findViewById(R.id.password_confirm_wrapper);
		passwordEntry =
				(EditText) setupActivity.findViewById(R.id.password_entry);
		passwordConfirmation =
				(EditText) setupActivity.findViewById(R.id.password_confirm);
		createAccountButton = (Button) setupActivity.findViewById(R.id.next);
	}

	private void testStrengthMeter(String pass, float strength, int color) {
		passwordEntry.setText(pass);
		assertEquals(strengthMeter.getProgress(),
				(int) (strengthMeter.getMax() * strength));
		assertEquals(color, strengthMeter.getColor());
	}

}
