package org.briarproject.bramble.test;

import org.junit.Test;

import static org.junit.Assert.fail;

public class ThreadExceptionTest extends BrambleTestCase {

	@Test(expected = AssertionError.class)
	public void testAssertionErrorMakesTestCaseFail() {
		// This is what BrambleTestCase does, too:
		fail();
	}

	@Test(expected = AssertionError.class)
	public void testExceptionInThreadMakesTestCaseFail() {
		Thread t = new Thread(() -> {
			System.out.println("thread before exception");
			throw new RuntimeException("boom");
		});

		t.start();
		try {
			t.join();
			System.out.println("joined thread");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}


}
