package org.briarproject.briar.socialbackup.recovery;

import org.briarproject.bramble.BrambleCoreIntegrationTestEagerSingletons;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.briar.api.socialbackup.recovery.CustodianTask;
import org.briarproject.briar.api.socialbackup.recovery.SecretOwnerTask;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.InetAddress;

import static junit.framework.TestCase.fail;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.junit.Assert.assertTrue;

public class ReturnShardIntegrationTest extends BrambleTestCase {

	private final File testDir = getTestDirectory();
	private final File ownerDir = new File(testDir, "owner");
	private final File custodianDir = new File(testDir, "custodian");

	private ReturnShardIntegrationTestComponent owner, custodian;

	@Before
	public void setUp() throws Exception {
		assertTrue(testDir.mkdirs());
		// Create the devices
		owner = DaggerReturnShardIntegrationTestComponent.builder()
				.testDatabaseConfigModule(
						new TestDatabaseConfigModule(ownerDir)).build();
		BrambleCoreIntegrationTestEagerSingletons.Helper
				.injectEagerSingletons(owner);
		custodian = DaggerReturnShardIntegrationTestComponent.builder()
				.testDatabaseConfigModule(new TestDatabaseConfigModule(
						custodianDir))
				.build();
		BrambleCoreIntegrationTestEagerSingletons.Helper
				.injectEagerSingletons(custodian);
	}

	@Test
	public void testReturnShard() {
		SecretOwnerTask secretOwnerTask = owner.getSecretOwnerTask();
		CustodianTask custodianTask = custodian.getCustodianTask();

		SecretOwnerTask.Observer ownerObserver =
				state -> {
					if (state instanceof SecretOwnerTask.State.Listening) {
						SecretOwnerTask.State.Listening listening =
								(SecretOwnerTask.State.Listening) state;
						byte[] payload = listening.getLocalPayload();
						System.out.println(payload.length);
						tansferQrCode(custodianTask, payload);
					} else if (state instanceof SecretOwnerTask.State.Failure) {
						System.out.println("owner state: failure");
						fail();
					} else {
						System.out.println(
								"owner: " + state.getClass().getSimpleName());
					}
				};

		CustodianTask.Observer custodianObserver =
				state -> System.out.println(
						"custodian: " + state.getClass().getSimpleName());

		owner.getIoExecutor().execute(() -> {
			try {
				secretOwnerTask
						.start(ownerObserver, InetAddress.getLocalHost());
			} catch (Exception e) {
				fail();
			}
		});

		custodian.getIoExecutor().execute(() -> {
			try {
				custodianTask.start(custodianObserver);
			} catch (Exception e) {
				fail();
			}
		});
	}

	private void tansferQrCode(CustodianTask custodianTask, byte[] payload) {
		System.out.println("Calling qrCodeDecoded in executor()");
		custodian.getIoExecutor().execute(() -> {
			try {
				System.out.println("Calling qrCodeDecoded()");
				Thread.sleep(500);
				custodianTask.qrCodeDecoded(payload);
				System.out.println("qrCodeDecoded() done");
			} catch (Exception e) {
				e.printStackTrace();
				fail();
			}
		});
	}

	private void tearDown(ReturnShardIntegrationTestComponent device)
			throws Exception {
		// Stop the lifecycle manager
		LifecycleManager lifecycleManager = device.getLifecycleManager();
		lifecycleManager.stopServices();
		lifecycleManager.waitForShutdown();
	}

	@After
	public void tearDown() throws Exception {
		tearDown(owner);
		tearDown(custodian);
		deleteTestDirectory(testDir);
	}

}
