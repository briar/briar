package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.BrambleCoreIntegrationTestEagerSingletons;
import org.briarproject.bramble.api.identity.Identity;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.mailbox.MailboxAuthToken;
import org.briarproject.bramble.api.mailbox.MailboxPairingState;
import org.briarproject.bramble.api.mailbox.MailboxPairingTask;
import org.briarproject.bramble.api.mailbox.MailboxProperties;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.mailbox.lib.AbstractMailbox;

import java.io.File;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.briarproject.bramble.api.mailbox.MailboxAuthToken.fromString;
import static org.briarproject.bramble.mailbox.MailboxTestUtils.getQrCodePayload;
import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.junit.Assert.fail;

abstract class AbstractMailboxIntegrationTest extends BrambleTestCase {

	static final String URL_BASE = "http://127.0.0.1:8000";

	private final File testDir = getTestDirectory();
	final File dir1 = new File(testDir, "alice");
	final File dir2 = new File(testDir, "bob");

	MailboxIntegrationTestComponent c1, c2;

	MailboxIntegrationTestComponent startTestComponent(
			File databaseDir, String name) throws Exception {
		MailboxIntegrationTestComponent component =
				DaggerMailboxIntegrationTestComponent
						.builder()
						.testDatabaseConfigModule(
								new TestDatabaseConfigModule(databaseDir))
						.build();
		BrambleCoreIntegrationTestEagerSingletons.Helper
				.injectEagerSingletons(component);
		setUp(component, name);
		return component;
	}

	private void setUp(MailboxIntegrationTestComponent device,
			String name) throws Exception {
		// Add an identity for the user
		IdentityManager identityManager = device.getIdentityManager();
		Identity identity = identityManager.createIdentity(name);
		identityManager.registerIdentity(identity);
		// Start the lifecycle manager
		LifecycleManager lifecycleManager = device.getLifecycleManager();
		lifecycleManager.startServices(getSecretKey());
		lifecycleManager.waitForStartup();
	}

	MailboxProperties pair(MailboxIntegrationTestComponent c,
			AbstractMailbox mailbox) throws Exception {
		MailboxAuthToken setupToken = fromString(mailbox.getSetupToken());

		MailboxPairingTask pairingTask = c.getMailboxManager()
				.startPairingTask(getQrCodePayload(setupToken.getBytes()));

		CountDownLatch latch = new CountDownLatch(1);
		pairingTask.addObserver((state) -> {
			if (state instanceof MailboxPairingState.Paired) {
				latch.countDown();
			}
		});
		if (!latch.await(10, SECONDS)) {
			fail("Timeout reached when waiting for pairing.");
		}
		return c.getDatabaseComponent()
				.transactionWithNullableResult(true, txn ->
						c.getMailboxSettingsManager()
								.getOwnMailboxProperties(txn)
				);
	}

}
