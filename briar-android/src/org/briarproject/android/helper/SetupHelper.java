package org.briarproject.android.helper;

public interface SetupHelper {
	float estimatePasswordStrength(String password);
	void createIdentity(String nickname, String password);
}
