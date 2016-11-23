package org.briarproject.briar.android;

class AndroidEagerSingletons {

	static void initEagerSingletons(AndroidComponent c) {
		c.inject(new AppModule.EagerSingletons());
	}
}
