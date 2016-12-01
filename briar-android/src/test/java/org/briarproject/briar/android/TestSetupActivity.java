package org.briarproject.briar.android;

import org.briarproject.briar.android.login.SetupActivity;
import org.briarproject.briar.android.login.SetupController;

/**
 * This class exposes the SetupController and offers the possibility to
 * override it.
 */
public class TestSetupActivity extends SetupActivity {

	public SetupController getController() {
		return setupController;
	}

	public void setController(SetupController setupController) {
		this.setupController = setupController;
	}
}
