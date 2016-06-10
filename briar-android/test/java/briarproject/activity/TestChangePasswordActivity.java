package briarproject.activity;

import org.briarproject.android.ChangePasswordActivity;
import org.briarproject.android.SetupActivity;
import org.briarproject.android.controller.PasswordController;
import org.briarproject.android.controller.SetupController;

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
