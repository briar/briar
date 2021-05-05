package org.briarproject.briar.remotewipe;

import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.briar.api.remotewipe.RemoteWipeManager;
import org.briarproject.briar.test.BriarIntegrationTest;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.briarproject.briar.test.DaggerBriarIntegrationTestComponent;
import org.junit.Before;
import org.junit.Test;

public class RemoteWipeIntegrationTest extends BriarIntegrationTest<BriarIntegrationTestComponent> {

	private RemoteWipeManager remoteWipeManager0;
	private RemoteWipeManager remoteWipeManager1;
	private RemoteWipeManager remoteWipeManager2;

	private Group g1From0;
	private Group g0From1;
	private Group g2From0;
	private Group g0From2;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		remoteWipeManager0 = c0.getRemoteWipeManager();
		remoteWipeManager1 = c1.getRemoteWipeManager();
		remoteWipeManager2 = c2.getRemoteWipeManager();

		g1From0 = remoteWipeManager0.getContactGroup(contact1From0);
		g0From1 = remoteWipeManager1.getContactGroup(contact0From1);
		g2From0 = remoteWipeManager0.getContactGroup(contact2From0);
		g0From2 = remoteWipeManager2.getContactGroup(contact0From2);
	}

	@Override
	protected void createComponents() {
		BriarIntegrationTestComponent component =
				DaggerBriarIntegrationTestComponent.builder().build();
		BriarIntegrationTestComponent.Helper.injectEagerSingletons(component);
		component.inject(this);

		c0 = DaggerBriarIntegrationTestComponent.builder()
				.testDatabaseConfigModule(new TestDatabaseConfigModule(t0Dir))
				.build();
		BriarIntegrationTestComponent.Helper.injectEagerSingletons(c0);

		c1 = DaggerBriarIntegrationTestComponent.builder()
				.testDatabaseConfigModule(new TestDatabaseConfigModule(t1Dir))
				.build();
		BriarIntegrationTestComponent.Helper.injectEagerSingletons(c1);

		c2 = DaggerBriarIntegrationTestComponent.builder()
				.testDatabaseConfigModule(new TestDatabaseConfigModule(t2Dir))
				.build();
		BriarIntegrationTestComponent.Helper.injectEagerSingletons(c2);
	}

	@Test
	public void testRemoteWipe() throws Exception {

	}
}
