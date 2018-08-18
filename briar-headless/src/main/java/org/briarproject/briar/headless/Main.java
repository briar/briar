package org.briarproject.briar.headless;

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

		DaggerBriarHeadlessApp
				.create()
				.router()
				.start();
	}

}
