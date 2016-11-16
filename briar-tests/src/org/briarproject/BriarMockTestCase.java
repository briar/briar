package org.briarproject;

import org.jmock.Mockery;
import org.junit.After;

public abstract class BriarMockTestCase extends BriarTestCase {

	protected final Mockery context = new Mockery();

	@After
	public void checkExpectations() {
		context.assertIsSatisfied();
	}
}
