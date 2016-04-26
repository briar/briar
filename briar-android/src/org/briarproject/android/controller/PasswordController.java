package org.briarproject.android.controller;

import org.briarproject.android.controller.handler.ResultHandler;

public interface PasswordController extends ConfigController {
	void validatePassword(String password,
			ResultHandler<Boolean> resultHandler);
}
