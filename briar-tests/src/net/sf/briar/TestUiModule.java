package net.sf.briar;

import net.sf.briar.api.ui.UiCallback;

import com.google.inject.AbstractModule;

public class TestUiModule extends AbstractModule {

	protected void configure() {
		bind(UiCallback.class).toInstance(new UiCallback() {

			public int showChoice(String[] options, String... message) {
				return -1;
			}

			public boolean showConfirmationMessage(String... message) {
				return false;
			}

			public void showMessage(String... message) {}			
		});
	}
}
