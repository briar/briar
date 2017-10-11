package org.briarproject.briar.android.login;

import android.support.design.widget.TextInputLayout;
import android.widget.EditText;

import org.briarproject.briar.R;
import org.briarproject.briar.android.TestBriarApplication;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, application = TestBriarApplication.class,
		packageName = "org.briarproject.briar")
public class SetupActivityTest {

	private SetupActivity setupActivity;
	private TextInputLayout nicknameEntryWrapper;
	private EditText nicknameEntry;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		setupActivity = Robolectric.setupActivity(SetupActivity.class);
		nicknameEntryWrapper =
				setupActivity.findViewById(R.id.nickname_entry_wrapper);
		nicknameEntry = setupActivity.findViewById(R.id.nickname_entry);
	}

	@Test
	public void testNicknameUI() {
		Assert.assertNotNull(setupActivity);
		String longNick = getRandomString(MAX_AUTHOR_NAME_LENGTH + 1);
		nicknameEntry.setText(longNick);
		// Nickname should be too long
		assertEquals(nicknameEntryWrapper.getError(),
				setupActivity.getString(R.string.name_too_long));
	}
}
