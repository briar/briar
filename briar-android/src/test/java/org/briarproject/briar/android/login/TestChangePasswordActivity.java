package org.briarproject.briar.android.login;

/**
 * This class exposes the PasswordController and SetupController and offers the
 * possibility to override them.
 */
public class TestChangePasswordActivity extends ChangePasswordActivity {

	public PasswordController getPasswordController() {
		return passwordController;
	}

	public void setPasswordController(PasswordController passwordController) {
		this.passwordController = passwordController;
	}

}
