package org.briarproject.briar.android.login;

import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.util.UiUtils;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static org.briarproject.bramble.api.crypto.PasswordStrengthEstimator.QUITE_WEAK;

public class PasswordFragment extends SetupFragment {

	private final static String TAG = PasswordFragment.class.getName();

	private TextInputLayout passwordEntryWrapper;
	private TextInputLayout passwordConfirmationWrapper;
	private TextInputEditText passwordEntry;
	private TextInputEditText passwordConfirmation;
	private StrengthMeter strengthMeter;
	private Button nextButton;
	private ProgressBar progressBar;

	public static PasswordFragment newInstance() {
		return new PasswordFragment();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		getActivity().setTitle(getString(R.string.setup_password_intro));
		View v = inflater.inflate(R.layout.fragment_setup_password, container,
						false);

		strengthMeter = v.findViewById(R.id.strength_meter);
		passwordEntryWrapper = v.findViewById(R.id.password_entry_wrapper);
		passwordEntry = v.findViewById(R.id.password_entry);
		passwordConfirmationWrapper =
				v.findViewById(R.id.password_confirm_wrapper);
		passwordConfirmation = v.findViewById(R.id.password_confirm);
		nextButton = v.findViewById(R.id.next);
		progressBar = v.findViewById(R.id.progress);

		passwordEntry.addTextChangedListener(this);
		passwordConfirmation.addTextChangedListener(this);
		nextButton.setOnClickListener(this);

		return v;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);

		// the controller is not yet available in onCreateView()
		if (!setupController.needToShowDozeFragment()) {
			nextButton.setText(R.string.create_account_button);
		}
	}

	@Override
	protected String getHelpText() {
		return getString(R.string.setup_password_explanation);
	}

	@Override
	public void onTextChanged(CharSequence authorName, int i, int i1, int i2) {
		String password1 = passwordEntry.getText().toString();
		String password2 = passwordConfirmation.getText().toString();
		boolean passwordsMatch = password1.equals(password2);

		strengthMeter
				.setVisibility(password1.length() > 0 ? VISIBLE : INVISIBLE);
		float strength = setupController.estimatePasswordStrength(password1);
		strengthMeter.setStrength(strength);
		boolean strongEnough = strength >= QUITE_WEAK;

		UiUtils.setError(passwordEntryWrapper,
				getString(R.string.password_too_weak),
				password1.length() > 0 && !strongEnough);
		UiUtils.setError(passwordConfirmationWrapper,
				getString(R.string.passwords_do_not_match),
				password2.length() > 0 && !passwordsMatch);

		boolean enabled = passwordsMatch && strongEnough;
		nextButton.setEnabled(enabled);
		passwordConfirmation.setOnEditorActionListener(enabled ? this : null);
	}

	@Override
	public void onClick(View view) {
		if (!setupController.needToShowDozeFragment()) {
			nextButton.setVisibility(INVISIBLE);
			progressBar.setVisibility(VISIBLE);
		}
		String password = passwordEntry.getText().toString();
		setupController.setPassword(password);
		setupController.showDozeOrCreateAccount();
	}

}
