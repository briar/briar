package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.test.TestDatabaseConfigModule;
import org.briarproject.briar.api.socialbackup.BackupMetadata;
import org.briarproject.briar.api.socialbackup.SocialBackupManager;
import org.briarproject.briar.test.BriarIntegrationTest;
import org.briarproject.briar.test.BriarIntegrationTestComponent;
import org.briarproject.briar.test.DaggerBriarIntegrationTestComponent;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import dagger.Provides;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class SocialBackupIntegrationTest
		extends BriarIntegrationTest<BriarIntegrationTestComponent> {

	private SocialBackupManager socialBackupManager0;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();
		socialBackupManager0 = c0.getSocialBackupManager();
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
	public void testCreateBackup() throws Exception {
		// Create the backup
		db0.transaction(false, txn -> {
			assertNull(socialBackupManager0.getBackupMetadata(txn));
			socialBackupManager0.createBackup(txn,
					asList(contactId1From0, contactId2From0), 2);
			BackupMetadata backupMetadata =
					socialBackupManager0.getBackupMetadata(txn);
			assertNotNull(backupMetadata);
			List<Author> expected = asList(contact1From0.getAuthor(),
					contact2From0.getAuthor());
			assertEquals(expected, backupMetadata.getCustodians());
			assertEquals(2, backupMetadata.getThreshold());
			assertEquals(0, backupMetadata.getVersion());
		});
		// Sync the shard and backup messages to the contacts
		sync0To1(2, true);
		sync0To2(2, true);
	}
}
