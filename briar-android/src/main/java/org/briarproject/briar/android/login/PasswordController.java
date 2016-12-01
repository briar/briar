package org.briarproject.briar.android.login;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.controller.ConfigController;
import org.briarproject.briar.android.controller.handler.ResultHandler;

@NotNullByDefault
public interface PasswordController extends ConfigController {

	void validatePassword(String password,
			ResultHandler<Boolean> resultHandler);

	void changePassword(String password, String newPassword,
			ResultHandler<Boolean> resultHandler);
}
