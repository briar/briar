package org.briarproject.briar.headless;

import org.briarproject.bramble.BrambleCoreModule;
import org.briarproject.briar.BriarCoreModule;

import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Main {

	public static void main(String[] args) {
		Logger rootLogger = LogManager.getLogManager().getLogger("");
		rootLogger.setLevel(Level.WARNING);

		for (String arg : args) {
			if (arg.equals("-v")) {
				rootLogger.setLevel(Level.INFO);
			}
		}

		BriarHeadlessApp app = DaggerBriarHeadlessApp.builder()
				.headlessModule(new HeadlessModule()).build();
		// We need to load the eager singletons directly after making the
		// dependency graphs
		BrambleCoreModule.initEagerSingletons(app);
		BriarCoreModule.initEagerSingletons(app);

		app.router().start();
	}

}
