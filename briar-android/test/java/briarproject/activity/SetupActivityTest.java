package briarproject.activity;

import android.content.Intent;
import android.support.design.widget.TextInputLayout;
import android.widget.Button;
import android.widget.EditText;

import com.google.common.base.Strings;

import org.briarproject.BuildConfig;
import org.briarproject.R;
import org.briarproject.android.NavDrawerActivity;
import org.briarproject.android.SetupActivity;
import org.briarproject.android.util.StrengthMeter;
import org.briarproject.api.identity.AuthorConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import java.util.logging.Logger;

import static briarproject.activity.MockedSetupActivity.NO_PASS;
import static briarproject.activity.MockedSetupActivity.QSTRONG_PASS;
import static briarproject.activity.MockedSetupActivity.QWEAK_PASS;
import static briarproject.activity.MockedSetupActivity.STRONG_PASS;
import static briarproject.activity.MockedSetupActivity.WEAK_PASS;
import static junit.framework.Assert.assertEquals;
import static org.briarproject.android.util.StrengthMeter.GREEN;
import static org.briarproject.android.util.StrengthMeter.LIME;
import static org.briarproject.android.util.StrengthMeter.ORANGE;
import static org.briarproject.android.util.StrengthMeter.RED;
import static org.briarproject.android.util.StrengthMeter.YELLOW;
import static org.briarproject.api.crypto.PasswordStrengthEstimator.NONE;
import static org.briarproject.api.crypto.PasswordStrengthEstimator.QUITE_STRONG;
import static org.briarproject.api.crypto.PasswordStrengthEstimator.QUITE_WEAK;
import static org.briarproject.api.crypto.PasswordStrengthEstimator.STRONG;
import static org.briarproject.api.crypto.PasswordStrengthEstimator.WEAK;
import static org.junit.Assert.assertNotEquals;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class SetupActivityTest {

	private static final Logger LOG =
			Logger.getLogger(SetupActivityTest.class.getName());


	private SetupActivity setupActivity;
	TextInputLayout nicknameEntryWrapper;
	TextInputLayout passwordEntryWrapper;
	TextInputLayout passwordConfirmationWrapper;
	EditText nicknameEntry;
	EditText passwordEntry;
	EditText passwordConfirmation;
	StrengthMeter strengthMeter;
	Button createAccountButton;

	@Before
	public void setUp() {
		setupActivity = Robolectric.setupActivity(MockedSetupActivity.class);
		nicknameEntryWrapper = (TextInputLayout) setupActivity
				.findViewById(R.id.nickname_entry_wrapper);
		passwordEntryWrapper = (TextInputLayout) setupActivity
				.findViewById(R.id.password_entry_wrapper);
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
	public void testUI() {
		// Nick
		String longNick =
				Strings.padEnd("*", AuthorConstants.MAX_AUTHOR_NAME_LENGTH + 1,
						'*');
		nicknameEntry.setText(longNick);
		assertEquals(nicknameEntryWrapper.getError(),
				setupActivity.getString(R.string.name_too_long));
		assertEquals(createAccountButton.isEnabled(), false);
		// strength estimator
		testStrengthMeter(STRONG_PASS, STRONG, GREEN);
		assertEquals(createAccountButton.isEnabled(), false);
		testStrengthMeter(QSTRONG_PASS, QUITE_STRONG, LIME);
		assertEquals(createAccountButton.isEnabled(), false);
		testStrengthMeter(QWEAK_PASS, QUITE_WEAK, YELLOW);
		assertEquals(createAccountButton.isEnabled(), false);
		testStrengthMeter(WEAK_PASS, WEAK, ORANGE);
		assertEquals(createAccountButton.isEnabled(), false);
		testStrengthMeter(NO_PASS, NONE, RED);
		assertEquals(createAccountButton.isEnabled(), false);

		// pass confirmation
		nicknameEntry.setText("nick.nickerton");
		passwordEntry.setText("really.safe.password");
		passwordConfirmation.setText("really.safe.pass");
		assertEquals(createAccountButton.isEnabled(), false);
		assertEquals(passwordConfirmationWrapper.getError(),
				setupActivity.getString(R.string.passwords_do_not_match));
		passwordEntry.setText("really.safe.pass");
		passwordConfirmation.setText("really.safe.pass");
		assertNotEquals(passwordConfirmationWrapper.getError(),
				setupActivity.getString(R.string.passwords_do_not_match));
		assertEquals(createAccountButton.isEnabled(), true);
		// confirm correct Activity started
		createAccountButton.performClick();
		assertEquals(setupActivity.isFinishing(), true);
		ShadowActivity shadowActivity = shadowOf(setupActivity);
		Intent intent = shadowActivity.peekNextStartedActivity();
		assertEquals(intent.getComponent().getClassName(),
				NavDrawerActivity.class.getName());
	}

}
