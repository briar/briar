package org.briarproject.briar.android.login;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.controller.handler.ResultHandler;

@NotNullByDefault
public interface ChangePasswordController {

	float estimatePasswordStrength(String password);

	void changePassword(String oldPassword, String newPassword,
			ResultHandler<Boolean> resultHandler);

}
