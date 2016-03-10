package org.briarproject.android;

public class AndroidEagerSingletons {

	public static void initEagerSingletons(AndroidComponent c) {
		c.inject(new AndroidModule.EagerSingletons());
	}
}
