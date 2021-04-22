package org.briarproject.briar.socialbackup.recovery;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.briar.api.socialbackup.BackupPayload;
import org.briarproject.briar.api.socialbackup.ContactData;
import org.briarproject.briar.api.socialbackup.DarkCrystal;
import org.briarproject.briar.api.socialbackup.ReturnShardPayload;
import org.briarproject.briar.api.socialbackup.Shard;
import org.briarproject.briar.api.socialbackup.SocialBackup;
import org.briarproject.briar.api.socialbackup.recovery.RestoreAccount;
import org.briarproject.briar.socialbackup.BackupPayloadDecoder;

import java.security.GeneralSecurityException;
import java.util.ArrayList;

import javax.inject.Inject;

public class RestoreAccountImpl implements RestoreAccount {
	private ArrayList<ReturnShardPayload> recoveredShards = new ArrayList<>();
	private final DarkCrystal darkCrystal;
	private final DatabaseComponent db;
	private final LifecycleManager lifecycleManager;
	private SecretKey secretKey;
	private final BackupPayloadDecoder backupPayloadDecoder;
	private SocialBackup socialBackup;

	@Inject
	RestoreAccountImpl(DarkCrystal darkCrystal,
			BackupPayloadDecoder backupPayloadDecoder, DatabaseComponent db,
			LifecycleManager lifecycleManager) {
		this.darkCrystal = darkCrystal;
		this.backupPayloadDecoder = backupPayloadDecoder;
		this.db = db;
		this.lifecycleManager = lifecycleManager;
	}

	public int getNumberOfShards() {
		return recoveredShards.size();
	}

	// TODO figure out how to actually use a hash set for these objects
	public boolean addReturnShardPayload(ReturnShardPayload toAdd) {
		boolean found = false;
		for (ReturnShardPayload returnShardPayload : recoveredShards) {
			if (toAdd.equals(returnShardPayload)) {
				found = true;
				break;
			}
		}
		if (!found) recoveredShards.add(toAdd);
		return !found;
	}

	public boolean canRecover() {
		ArrayList<Shard> shards = new ArrayList();
		for (ReturnShardPayload returnShardPayload : recoveredShards) {
			// TODO check shards all have same secret id
			shards.add(returnShardPayload.getShard());
		}
		try {
			secretKey = darkCrystal.combineShards(shards);
		} catch (GeneralSecurityException e) {
			// TODO handle error message
			return false;
		}
		return true;
	}

	public int recover() throws FormatException, GeneralSecurityException {
		if (secretKey == null) throw new GeneralSecurityException();
		// Find backup with highest version number
		int highestVersion = -1;
		for (ReturnShardPayload returnShardPayload : recoveredShards) {
			BackupPayload backupPayload = returnShardPayload.getBackupPayload();
			SocialBackup s = backupPayloadDecoder
					.decodeBackupPayload(secretKey, backupPayload);
			if (s.getVersion() > highestVersion) {
				socialBackup = s;
				highestVersion = s.getVersion();
			}
		}
		return highestVersion;
	}

	public SocialBackup getSocialBackup() {
		return socialBackup;
	}

	public void addContactsToDb() throws InterruptedException, DbException {
		if (socialBackup == null) throw new DbException();
		// TODO maybe waitForDatabase should be in another thread
		lifecycleManager.waitForDatabase();
		db.transaction(false, txn -> {
			for (ContactData contactData : socialBackup.getContacts()) {
				Contact c = contactData.getContact();
				db.addContact(txn, c.getAuthor(), c.getLocalAuthorId(),
						c.getHandshakePublicKey(), c.isVerified());
			}
		});
	}
}
