package org.briarproject.briar.android.login;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.controller.handler.ResultHandler;

@NotNullByDefault
public interface SetupController {

	float estimatePasswordStrength(String password);

	void storeAuthorInfo(String nickname, String password,
			ResultHandler<Void> resultHandler);

}
