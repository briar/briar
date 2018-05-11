package org.briarproject.briar.android.login;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface SetupController {

	void setSetupActivity(SetupActivity setupActivity);

	boolean needToShowDozeFragment();

	void setAuthorName(String authorName);

	void setPassword(String password);

	float estimatePasswordStrength(String password);

	/**
	 * This should be called after the author name has been set.
	 */
	void showPasswordFragment();

	/**
	 * This should be called after the author name and the password have been
	 * set. It decides whether to show the doze fragment or create the account
	 * right away.
	 */
	void showDozeFragmentOrCreateAccount();

	void createAccount();
}
