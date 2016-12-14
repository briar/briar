package org.briarproject.bramble.test;

import org.jmock.Mockery;
import org.junit.After;

public abstract class BrambleMockTestCase extends
		BrambleTestCase {

	protected final Mockery context = new Mockery();

	@After
	public void checkExpectations() {
		context.assertIsSatisfied();
	}
}
