package org.briarproject.briar.android.login;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.controller.handler.ResultHandler;

@NotNullByDefault
public interface SetupController {

	void setSetupActivity(SetupActivity setupActivity);

	boolean needToShowDozeFragment();

	void setAuthorName(String authorName);

	void setPassword(String password);

	float estimatePasswordStrength(String password);

	/**
	 * This should be called after the author name and the password have been
	 * set. It decides whether to ask for doze exception or create the account
	 * right away.
	 */
	void showDozeOrCreateAccount();

	void createAccount();

	void createAccount(ResultHandler<Void> resultHandler);

}
