package org.briarproject.android;

import org.briarproject.android.controller.SetupController;

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
