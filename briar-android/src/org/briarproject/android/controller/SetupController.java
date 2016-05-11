package org.briarproject.android.controller;

import org.briarproject.android.controller.handler.ResultHandler;

public interface SetupController {
	float estimatePasswordStrength(String password);

	void createIdentity(String nickname, String password,
			ResultHandler<Long> resultHandler);
}
