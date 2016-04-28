package briarproject.activity;

import android.support.design.widget.TextInputLayout;
import android.widget.Button;
import android.widget.EditText;

import org.briarproject.BuildConfig;
import org.briarproject.R;
import org.briarproject.android.ActivityModule;
import org.briarproject.android.SetupActivity;
import org.briarproject.android.util.StrengthMeter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 22)
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
			return super.getActivityModule();
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



	}
}
