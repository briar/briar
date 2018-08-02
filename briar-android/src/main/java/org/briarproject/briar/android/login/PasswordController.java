package org.briarproject.briar.android.login;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.controller.handler.ResultHandler;

@NotNullByDefault
public interface PasswordController {

	float estimatePasswordStrength(String password);

	void validatePassword(String password,
			ResultHandler<Boolean> resultHandler);

	void changePassword(String oldPassword, String newPassword,
			ResultHandler<Boolean> resultHandler);

}
