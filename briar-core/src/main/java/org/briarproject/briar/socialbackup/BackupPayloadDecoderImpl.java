package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.AgreementPrivateKey;
import org.briarproject.bramble.api.crypto.AgreementPublicKey;
import org.briarproject.bramble.api.crypto.AuthenticatedCipher;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.crypto.SignaturePrivateKey;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.Identity;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.briar.api.socialbackup.BackupPayload;
import org.briarproject.briar.api.socialbackup.MessageParser;
import org.briarproject.briar.api.socialbackup.Shard;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Provider;

import static java.util.logging.Logger.getLogger;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.AUTH_TAG_BYTES;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.NONCE_BYTES;

public class BackupPayloadDecoderImpl implements BackupPayloadDecoder {
	private final ClientHelper clientHelper;
	private final Provider<AuthenticatedCipher> cipherProvider;
	private final SecureRandom secureRandom;
	private final MessageParser messageParser;
	private static final Logger LOG =
			getLogger(BackupPayloadDecoderImpl.class.getName());

	@Inject
	BackupPayloadDecoderImpl(ClientHelper clientHelper,
			Provider<AuthenticatedCipher> cipherProvider,
			SecureRandom secureRandom,
			MessageParser messageParser) {
		this.clientHelper = clientHelper;
		this.cipherProvider = cipherProvider;
		this.secureRandom = secureRandom;
		this.messageParser = messageParser;
	}

	public SocialBackup decodeBackupPayload(
			SecretKey secret,
			BackupPayload backupPayload)
			throws FormatException, GeneralSecurityException {

		byte[] ciphertextWithNonce = backupPayload.getBytes();
		byte[] nonce = new byte[NONCE_BYTES];
		System.arraycopy(ciphertextWithNonce, 0, nonce, 0, NONCE_BYTES);

		byte[] ciphertext = new byte[ciphertextWithNonce.length - NONCE_BYTES];
		System.arraycopy(ciphertextWithNonce, nonce.length, ciphertext, 0,
				ciphertext.length);

		AuthenticatedCipher cipher = cipherProvider.get();
		cipher.init(false, secret, nonce);
		byte[] plaintext =
				new byte[ciphertext.length - AUTH_TAG_BYTES];
		int decrypted = cipher.process(ciphertext, 0,
				ciphertext.length, plaintext, 0);
		if (decrypted != plaintext.length) throw new AssertionError();
		LOG.info("Backup payload decrypted");

		BdfList backup = clientHelper.toList(plaintext);
		int version = backup.getLong(0).intValue();
		LOG.info("Backup payload has version number " + version);

		BdfList bdfIdentity = backup.getList(1);
		BdfList bdfContactData = backup.getList(2);

		Author a = clientHelper
				.parseAndValidateAuthor(bdfIdentity.getList(0));
		PrivateKey signaturePrivateKey =
				new SignaturePrivateKey(bdfIdentity.getRaw(1));
		LocalAuthor localAuthor =
				new LocalAuthor(a.getId(), a.getFormatVersion(), a.getName(),
						a.getPublicKey(), signaturePrivateKey);
		LOG.info("LocalAuthor parsed successfully. Name is " + a.getName());

		PublicKey handshakePublicKey =
				new AgreementPublicKey(bdfIdentity.getRaw(2));
		PrivateKey handShakePrivateKey =
				new AgreementPrivateKey(bdfIdentity.getRaw(3));

		Long created = System.currentTimeMillis();

		Identity identity = new Identity(localAuthor, handshakePublicKey,
				handShakePrivateKey, created);
		LOG.info("New identity created");

		List<ContactData> contactDataList = new ArrayList();

		for (int i = 0; i < bdfContactData.size(); i++) {
			BdfList bdfData = bdfContactData.getList(i);

			Author author =
					clientHelper.parseAndValidateAuthor(bdfData.getList(0));
			LOG.info("Contact author parsed");

			String alias = bdfData.getOptionalString(1);
			LOG.info("Contact alias is: " + alias);

			// 2 - public key or null
			byte[] handshakePublicKeyBytes = bdfData.getOptionalRaw(2);
			PublicKey contactHandshakePublicKey = (handshakePublicKeyBytes == null)
					? null
					: new AgreementPublicKey(handshakePublicKeyBytes);
			LOG.info("Contact handshake pk parsed");

			// 3 - properties dictionary
			Map<TransportId, TransportProperties> properties = clientHelper
					.parseAndValidateTransportPropertiesMap(
							bdfData.getDictionary(3));
			LOG.info("Contact transport properties parsed");

			// 4 shard or null
			BdfList shardList = bdfData.getOptionalList(4);
			Shard shard = (shardList == null) ? null :
					messageParser.parseShardMessage(shardList);
			// TODO validate shard
		    LOG.info("Contact shard parsed");

			ContactId contactId = new ContactId(i);
			Contact contact =
					new Contact(contactId, author, author.getId(), alias,
							contactHandshakePublicKey, false);
			ContactData contactData =
					new ContactData(contact, properties, shard);
			contactDataList.add(contactData);
			LOG.info("Contact added");
		}
		LOG.info("All contacts added");
		return new SocialBackup(identity, contactDataList, version);
	}
}
