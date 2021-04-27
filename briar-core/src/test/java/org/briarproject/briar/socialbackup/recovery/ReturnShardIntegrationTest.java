package org.briarproject.briar.socialbackup.recovery;

import org.briarproject.bramble.BrambleCoreIntegrationTestEagerSingletons;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.briar.api.socialbackup.BackupPayload;
import org.briarproject.briar.api.socialbackup.MessageEncoder;
import org.briarproject.briar.api.socialbackup.ReturnShardPayload;
import org.briarproject.briar.api.socialbackup.Shard;
import org.briarproject.briar.api.socialbackup.recovery.CustodianTask;
import org.briarproject.briar.api.socialbackup.recovery.SecretOwnerTask;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
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

	private ReturnShardPayload remotePayload;

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
	public void testReturnShard() throws Exception {
		SecretOwnerTask secretOwnerTask = owner.getSecretOwnerTask();
		CustodianTask custodianTask = custodian.getCustodianTask();
		MessageEncoder messageEncoder = owner.getMessageEncoder();

		CountDownLatch secretOwnerFinished = new CountDownLatch(1);
		CountDownLatch custodianFinished = new CountDownLatch(1);

		Shard shard = new Shard("secret id".getBytes(), "shard".getBytes());
		BackupPayload backupPayload = new BackupPayload("backup payload".getBytes());
        ReturnShardPayload returnShardPayload = new ReturnShardPayload(shard, backupPayload);

        byte[] payloadBytes = messageEncoder.encodeReturnShardPayload(returnShardPayload);


		SecretOwnerTask.Observer ownerObserver =
				state -> {
					if (state instanceof SecretOwnerTask.State.Listening) {
						SecretOwnerTask.State.Listening listening =
								(SecretOwnerTask.State.Listening) state;
						byte[] qrPayload = listening.getLocalPayload();
						transferQrCode(custodianTask, qrPayload);
					} else if (state instanceof SecretOwnerTask.State.Success) {
						remotePayload = ((SecretOwnerTask.State.Success) state).getRemotePayload();
						secretOwnerFinished.countDown();
					} else if (state instanceof SecretOwnerTask.State.Failure) {
						fail();
					}
				};

		CustodianTask.Observer custodianObserver =
				state -> {
					if (state instanceof CustodianTask.State.Success) {
						custodianFinished.countDown();
					} else if (state instanceof CustodianTask.State.Failure) {
						fail();
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
				custodianTask.start(custodianObserver, payloadBytes);
			} catch (Exception e) {
				fail();
			}
		});
        assertTrue(secretOwnerFinished.await(15000, MILLISECONDS));
		assertTrue(custodianFinished.await(15000, MILLISECONDS));
		assertTrue(remotePayload.equals(returnShardPayload));

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
