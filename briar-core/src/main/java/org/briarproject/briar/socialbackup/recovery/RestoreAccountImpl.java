package org.briarproject.briar.socialbackup.recovery;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.api.properties.TransportPropertyManager;
import org.briarproject.briar.api.socialbackup.BackupPayload;
import org.briarproject.briar.api.socialbackup.ContactData;
import org.briarproject.briar.api.socialbackup.DarkCrystal;
import org.briarproject.briar.api.socialbackup.MessageEncoder;
import org.briarproject.briar.api.socialbackup.MessageParser;
import org.briarproject.briar.api.socialbackup.ReturnShardPayload;
import org.briarproject.briar.api.socialbackup.Shard;
import org.briarproject.briar.api.socialbackup.SocialBackup;
import org.briarproject.briar.api.socialbackup.recovery.RestoreAccount;
import org.briarproject.briar.socialbackup.BackupPayloadDecoder;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Logger.getLogger;

public class RestoreAccountImpl implements RestoreAccount {
	private final Set<ReturnShardPayload> recoveredShards = new HashSet<>();
	private final DarkCrystal darkCrystal;
	private final Executor ioExecutor;
	private final DatabaseComponent db;
	private final ContactManager contactManager;
	private final MessageEncoder messageEncoder;
	private final MessageParser messageParser;
	private final TransportPropertyManager transportPropertyManager;
	private final LifecycleManager lifecycleManager;
	private SecretKey secretKey;
	private final BackupPayloadDecoder backupPayloadDecoder;
	private final ClientHelper clientHelper;
	private SocialBackup socialBackup;
	private byte[] secretId;

	private static final Logger LOG =
			getLogger(RestoreAccountImpl.class.getName());

	@Inject
	RestoreAccountImpl(DarkCrystal darkCrystal,
			BackupPayloadDecoder backupPayloadDecoder, DatabaseComponent db,
			@IoExecutor Executor ioExecutor,
			ContactManager contactManager,
			LifecycleManager lifecycleManager,
			TransportPropertyManager transportPropertyManager,
			MessageEncoder messageEncoder,
			MessageParser messageParser,
			ClientHelper clientHelper) {
		this.darkCrystal = darkCrystal;
		this.backupPayloadDecoder = backupPayloadDecoder;
		this.db = db;
		this.ioExecutor = ioExecutor;
		this.lifecycleManager = lifecycleManager;
		this.contactManager = contactManager;
		this.transportPropertyManager = transportPropertyManager;
		this.messageEncoder = messageEncoder;
		this.messageParser = messageParser;
		this.clientHelper = clientHelper;
	}

	public int getNumberOfShards() {
		return recoveredShards.size();
	}

	public AddReturnShardPayloadResult addReturnShardPayload(
			ReturnShardPayload toAdd) {
		AddReturnShardPayloadResult result = AddReturnShardPayloadResult.OK;
		// TODO figure out how to actually use a hash set for these objects
		for (ReturnShardPayload returnShardPayload : recoveredShards) {
			if (toAdd.equals(returnShardPayload)) {
				return AddReturnShardPayloadResult.DUPLICATE;
			}
		}

		if (secretId == null) secretId = toAdd.getShard().getSecretId();
		if (!Arrays.equals(secretId, toAdd.getShard().getSecretId())) {
			return AddReturnShardPayloadResult.MISMATCH;
		}
		recoveredShards.add(toAdd);
		return canRecover() ? AddReturnShardPayloadResult.RECOVERED :
				AddReturnShardPayloadResult.OK;
	}

	public boolean canRecover() {
		ArrayList<Shard> shards = new ArrayList<>();
		for (ReturnShardPayload returnShardPayload : recoveredShards) {
			// TODO check shards all have same secret id
			Shard shard = returnShardPayload.getShard();
//			shard.getSecretId();
			shards.add(shard);
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

	public void restoreAccountWhenDatabaseReady() throws DbException {
		ioExecutor.execute(() -> {
			try {
				lifecycleManager.waitForDatabase();
			} catch (InterruptedException e) {
				LOG.warning("Interrupted when waiting for database");
			}
			try {
				addLocalTransportProperties();
				addContactsToDb();
			} catch (DbException e) {
				LOG.warning("Error when processing backup");
				e.printStackTrace();
			}
		});
	}


	private void addContactsToDb() throws DbException {
		if (socialBackup == null) throw new DbException();
		AuthorId localAuthorId = socialBackup.getIdentity().getId();

		try {
			db.transaction(false, txn -> {
				for (ContactData contactData : socialBackup.getContacts()) {
					Contact c = contactData.getContact();
					LOG.info("Adding contact " + c.getAuthor().getName() +
							" " + c.getAlias());
					if (c.getHandshakePublicKey() == null) {
						LOG.warning("Warning: contact has no handshake public key");
					}
					ContactId contactId = contactManager
							.addContact(txn, c.getAuthor(), localAuthorId,
									c.getHandshakePublicKey(),
									c.isVerified());
					transportPropertyManager
							.addRemoteProperties(txn, contactId,
									contactData.getProperties());
				}
			});
		} catch (DbException e) {
			LOG.warning("Error adding contacts to database");
			LOG.warning(e.getMessage());
		}
		LOG.info("Added all contacts");
	}

	private void addLocalTransportProperties()
			throws DbException {
		LOG.info("Adding local transport properties");
		for (Map.Entry<TransportId, TransportProperties> propertiesEntry : socialBackup
				.getLocalTransportProperties().entrySet()) {
			LOG.info("Adding transport property " +
					propertiesEntry.getKey().getString());
			transportPropertyManager
					.mergeLocalProperties(propertiesEntry.getKey(),
							propertiesEntry.getValue());
		}
	}

	public Set<String> getEncodedShards() {
		Set<String> s = new HashSet();
		for (ReturnShardPayload r : recoveredShards) {
			s.add(new String(messageEncoder.encodeReturnShardPayload(r)));
		}
		return s;
	}

	public void restoreFromPrevious(Set<String> previousShards) {
		for (String s : previousShards) {
			try {
				addReturnShardPayload(messageParser.parseReturnShardPayload(
						clientHelper.toList(s.getBytes())));
			} catch (FormatException e) {
				e.printStackTrace();
			}
		}
	}
}
