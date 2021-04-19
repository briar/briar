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

import javax.inject.Inject;
import javax.inject.Provider;

import static org.briarproject.briar.socialbackup.SocialBackupConstants.AUTH_TAG_BYTES;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.NONCE_BYTES;

public class BackupPayloadDecoderImpl {
	private final ClientHelper clientHelper;
	private final Provider<AuthenticatedCipher> cipherProvider;
	private final SecureRandom secureRandom;
	private final MessageParser messageParser;

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
		System.arraycopy(ciphertextWithNonce, nonce.length, ciphertext, 0, ciphertext.length);

		AuthenticatedCipher cipher = cipherProvider.get();
		cipher.init(false, secret, nonce);
		byte[] plaintext =
				new byte[ciphertext.length - AUTH_TAG_BYTES];
		int decrypted = cipher.process(ciphertext, 0,
				ciphertext.length, plaintext, 0);
		if (decrypted != plaintext.length) throw new AssertionError();

		BdfList backup = clientHelper.toList(plaintext);
		int version = backup.getLong(0).intValue();
		BdfList bdfIdentity = backup.getList(1);
		BdfList bdfContactData = backup.getList(2);

		LocalAuthor localAuthor =
				(LocalAuthor) clientHelper
						.parseAndValidateAuthor(bdfIdentity.getList(0));
		//TODO
		byte[] authorPrivateKeyBytes = bdfIdentity.getRaw(1);

		PublicKey handshakePublicKey =
				new AgreementPublicKey(bdfIdentity.getRaw(2));
		PrivateKey handShakePrivateKey =
				new AgreementPrivateKey(bdfIdentity.getRaw(3));

		Long created = System.currentTimeMillis();

		Identity identity = new Identity(localAuthor, handshakePublicKey,
				handShakePrivateKey, created);

		List<ContactData> contactDataList = new ArrayList();

		for (int i = 0; i < bdfContactData.size(); i++) {
			BdfList bdfData = bdfContactData.getList(i);

			Author author =
					clientHelper.parseAndValidateAuthor(bdfData.getList(0));
			String alias = bdfData.getString(1);
			// 2 - public key or null
			byte[] publicKeyBytes = bdfData.getRaw(2);

			// 3 - properties dictionary
			Map<TransportId, TransportProperties> properties = clientHelper
					.parseAndValidateTransportPropertiesMap(
							bdfData.getDictionary(3));
			// 4 shard or null
			BdfList shardList = bdfData.getList(4);
			Shard shard = shardList == null ? null :
					messageParser.parseShardMessage(shardList);
			ContactId contactId = new ContactId(i);
			Contact contact =
					new Contact(contactId, author, author.getId(), alias,
							handshakePublicKey, false);
			ContactData contactData =
					new ContactData(contact, properties, shard);
			contactDataList.add(contactData);
		}
		return new SocialBackup(identity, contactDataList, version);
	}
}
