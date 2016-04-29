package briarproject.activity;

import android.support.design.widget.TextInputLayout;
import android.widget.Button;
import android.widget.EditText;

import com.google.common.base.Strings;

import org.briarproject.BuildConfig;
import org.briarproject.R;
import org.briarproject.android.ActivityModule;
import org.briarproject.android.SetupActivity;
import org.briarproject.android.controller.SetupController;
import org.briarproject.android.controller.SetupControllerImp;
import org.briarproject.android.util.StrengthMeter;
import org.briarproject.api.crypto.PasswordStrengthEstimator;
import org.briarproject.api.identity.AuthorConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21)
public class SetupActivityTest {

	private SetupActivity setupActivity;
	TextInputLayout nicknameEntryWrapper;
	TextInputLayout passwordEntryWrapper;
	TextInputLayout passwordConfirmationWrapper;
	EditText nicknameEntry;
	EditText passwordEntry;
	EditText passwordConfirmation;
	StrengthMeter strengthMeter;
	Button createAccountButton;

	class TestSetupActivity extends SetupActivity {

		@Override
		protected ActivityModule getActivityModule() {
			return new ActivityModule(this) {

				@Override
				protected SetupController provideSetupController(
						SetupControllerImp setupControllerImp) {
					SetupController setupController =
							Mockito.mock(SetupControllerImp.class);
					Mockito.when(
							setupController.estimatePasswordStrength("strong"))
							.thenReturn(PasswordStrengthEstimator.STRONG);
//					Mockito.when(
//							setupController.estimatePasswordStrength("qstrong"))
//							.thenReturn(PasswordStrengthEstimator.QUITE_STRONG);
					return setupController;
				}
			};
		}
	}

	@Before
	public void setUp() {
		setupActivity = Robolectric.setupActivity(SetupActivity.class);
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

	@Test
	public void test() {
		String longNick =
				Strings.padEnd("*", AuthorConstants.MAX_AUTHOR_NAME_LENGTH + 1,
						'*');
		nicknameEntry.setText(longNick);
		assertEquals(nicknameEntryWrapper.getError(),
				setupActivity.getString(R.string.name_too_long));

		passwordEntry.setText("strong");
		assertEquals(strengthMeter.getProgress(),
				strengthMeter.getMax() * PasswordStrengthEstimator.STRONG);

//		passwordEntry.setText("strong");
//		assertEquals(StrengthMeter.GREEN, strengthMeter.getColor());
//		setupActivity.
	}
}
