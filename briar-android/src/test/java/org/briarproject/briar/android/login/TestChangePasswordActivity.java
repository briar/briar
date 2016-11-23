package org.briarproject.briar.android.login;

/**
 * This class exposes the PasswordController and SetupController and offers the
 * possibility to override them.
 */
public class TestChangePasswordActivity extends ChangePasswordActivity {

	public PasswordController getPasswordController() {
		return passwordController;
	}

	public SetupController getSetupController() {
		return setupController;
	}

	public void setPasswordController(PasswordController passwordController) {
		this.passwordController = passwordController;
	}

	public void setSetupController(SetupController setupController) {
		this.setupController = setupController;
	}
}
