package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Before;
import org.junit.Test;

import static org.briarproject.bramble.mailbox.MailboxIntegrationTestUtils.createTestComponent;
import static org.junit.Assert.assertTrue;

public class OwnMailboxContactListWorkerIntegrationTest
		extends BrambleTestCase {

	private MailboxIntegrationTestComponent component;

	@Before
	public void setUp() {
		component = createTestComponent();
	}

	// Just test that we can build the component. TODO: Write actual tests
	@Test
	public void testBuild() {
		assertTrue(true);
	}
}
