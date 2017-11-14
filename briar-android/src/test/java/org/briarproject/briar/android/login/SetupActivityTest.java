package org.briarproject.briar.android.login;

import android.support.design.widget.TextInputLayout;
import android.widget.EditText;

import com.google.common.base.Strings;

import org.briarproject.bramble.api.identity.AuthorConstants;
import org.briarproject.briar.BuildConfig;
import org.briarproject.briar.R;
import org.briarproject.briar.android.TestBriarApplication;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21,
		application = TestBriarApplication.class,
		packageName = "org.briarproject.briar")
public class SetupActivityTest {

	private SetupActivity setupActivity;
	private TextInputLayout nicknameEntryWrapper;
	private EditText nicknameEntry;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		setupActivity = Robolectric.setupActivity(SetupActivity.class);
		nicknameEntryWrapper = (TextInputLayout) setupActivity
				.findViewById(R.id.nickname_entry_wrapper);
		nicknameEntry =
				(EditText) setupActivity.findViewById(R.id.nickname_entry);
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
}
