package org.briarproject.briar.socialbackup.recovery;

import org.briarproject.bramble.BrambleCoreIntegrationTestEagerSingletons;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.briar.api.socialbackup.BackupPayload;
import org.briarproject.briar.api.socialbackup.ReturnShardPayload;
import org.briarproject.briar.api.socialbackup.Shard;
import org.briarproject.briar.api.socialbackup.recovery.CustodianTask;
import org.briarproject.briar.api.socialbackup.recovery.SecretOwnerTask;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.Executor;

import static junit.framework.TestCase.fail;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.junit.Assert.assertEquals;
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
		byte[] payload = "its nice to be important but its more important to be nice".getBytes();

		Shard shard = new Shard("secretid".getBytes(), "shard".getBytes());
		BackupPayload backupPayload = new BackupPayload("backup payload".getBytes());
        ReturnShardPayload returnShardPayload = new ReturnShardPayload(shard, backupPayload);

//        payloadBytes = clientHelper

		SecretOwnerTask.Observer ownerObserver =
				state -> {
					if (state instanceof SecretOwnerTask.State.Listening) {
						SecretOwnerTask.State.Listening listening =
								(SecretOwnerTask.State.Listening) state;
						byte[] qrPayload = listening.getLocalPayload();
						System.out.println(qrPayload.length);
						transferQrCode(custodianTask, qrPayload);
					} else if (state instanceof SecretOwnerTask.State.Success) {
						ReturnShardPayload remotePayload = ((SecretOwnerTask.State.Success) state).getRemotePayload();
						assertTrue(remotePayload.equals(payload));
						System.out.println("Success");
					} else if (state instanceof SecretOwnerTask.State.Failure) {
						System.out.println("Owner state: failure");
						fail();
					} else {
						System.out.println(
								"owner: " + state.getClass().getSimpleName());
					}
				};

		CustodianTask.Observer custodianObserver =
				state -> {
					if (state instanceof CustodianTask.State.Success) {
						assertEquals(1, 1);
					} else if (state instanceof CustodianTask.State.Failure) {
						fail();
					} else {
						System.out.println(
								"custodian: " + state.getClass().getSimpleName());
					}
				};

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
				custodianTask.start(custodianObserver, payload);
			} catch (Exception e) {
				fail();
			}
		});

		// TODO how to get the test to wait for the io to finish
		try {
//			Thread.sleep(1000);
			tearDown();
		} catch (Exception e) {
			fail();
		}

	}

	private void transferQrCode(CustodianTask custodianTask, byte[] payload) {
		custodian.getIoExecutor().execute(() -> {
			try {
				custodianTask.qrCodeDecoded(payload);
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
