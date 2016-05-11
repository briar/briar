package briarproject.activity;

import org.briarproject.android.SetupActivity;
import org.briarproject.android.controller.SetupController;

/**
 * This class exposes the SetupController and offers the possibility to
 * override it.
 */
public class TestSetupActivity extends SetupActivity {

	public SetupController getController() {
		return this.setupController;
	}

	public void setController(SetupController setupController) {
		this.setupController = setupController;
	}

}
