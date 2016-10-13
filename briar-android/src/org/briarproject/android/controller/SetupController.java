package org.briarproject.android.controller;

import org.briarproject.android.controller.handler.ResultHandler;

public interface SetupController {

	float estimatePasswordStrength(String password);

	void storeAuthorInfo(String password, String nickName,
			ResultHandler<Void> resultHandler);

}
