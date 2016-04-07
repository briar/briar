package org.briarproject.android.controller;

public interface PasswordController extends ConfigController {
	void validatePassword(String password,
			ResultHandler<Boolean, EncryptedKeyNullException> resultHandler);
}
