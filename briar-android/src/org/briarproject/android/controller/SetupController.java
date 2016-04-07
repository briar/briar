package org.briarproject.android.controller;

public interface SetupController {
	float estimatePasswordStrength(String password);

	void createIdentity(String nickname, String password,
			ResultHandler<Long, RuntimeException> resultHandler);
}
