package org.briarproject.briar.android.login;

/**
 * This class exposes the PasswordController and offers the possibility to
 * replace it.
 */
public class TestChangePasswordActivity extends ChangePasswordActivity {

	public void setPasswordController(PasswordController passwordController) {
		this.passwordController = passwordController;
	}

}
