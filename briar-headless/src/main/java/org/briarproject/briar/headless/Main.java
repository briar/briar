package org.briarproject.briar.headless;

public class Main {

	public static void main(String[] args) {
		DaggerBriarHeadlessApp
				.create()
				.router()
				.start();
	}

}
