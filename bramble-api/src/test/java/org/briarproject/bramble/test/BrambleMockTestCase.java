package org.briarproject.bramble.test;

import org.jmock.Mockery;
import org.jmock.lib.concurrent.Synchroniser;
import org.junit.After;

public abstract class BrambleMockTestCase extends BrambleTestCase {

	protected final Mockery context = new Mockery();

	public BrambleMockTestCase() {
		context.setThreadingPolicy(new Synchroniser());
	}

	@After
	public void checkExpectations() {
		context.assertIsSatisfied();
	}
}
