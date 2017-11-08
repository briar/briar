package org.briarproject.briar.android.login;

import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import org.briarproject.briar.BuildConfig;
import org.briarproject.briar.R;
import org.briarproject.briar.android.TestBriarApplication;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertEquals;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.STRONG;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.shadows.support.v4.SupportFragmentTestUtil.startFragment;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21,
		application = TestBriarApplication.class,
		packageName = "org.briarproject.briar")
public class PasswordFragmentTest {

	private PasswordFragment passwordFragment = new PasswordFragment();
	private EditText passwordEntry;
	private EditText passwordConfirmation;
	private Button createAccountButton;

	@Mock
	private SetupController setupController;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		startFragment(passwordFragment, SetupActivity.class);

		View v = passwordFragment.getView();
		passwordEntry = (EditText) v.findViewById(R.id.password_entry);
		passwordConfirmation = (EditText) v.findViewById(R.id.password_confirm);
		createAccountButton = (Button) v.findViewById(R.id.next);
	}

	@Test
	public void testCreateAccountUI() {
		passwordFragment.setupController = setupController;
		when(setupController.needsDozeWhitelisting()).thenReturn(false);
		when(setupController.estimatePasswordStrength(anyString()))
				.thenReturn(STRONG);

		String safePass = "really.safe.password";
		passwordEntry.setText(safePass);
		passwordConfirmation.setText(safePass);
		// Confirm that the create account button is clickable
		assertEquals(createAccountButton.isEnabled(), true);
		createAccountButton.performClick();

		// assert controller has been called properly
		verify(setupController, times(1))
				.setPassword(safePass);
		verify(setupController, times(1))
				.showDozeOrCreateAccount();
	}

}
