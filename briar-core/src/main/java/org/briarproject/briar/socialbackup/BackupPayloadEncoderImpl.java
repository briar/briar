package org.briarproject.briar.socialbackup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.crypto.AuthenticatedCipher;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.identity.Identity;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.briar.api.socialbackup.BackupPayload;
import org.briarproject.briar.api.socialbackup.Shard;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import javax.inject.Provider;

import static org.briarproject.briar.socialbackup.SocialBackupConstants.AUTH_TAG_BYTES;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.NONCE_BYTES;

@Immutable
@NotNullByDefault
class BackupPayloadEncoderImpl implements BackupPayloadEncoder {

	private final ClientHelper clientHelper;
	private final Provider<AuthenticatedCipher> cipherProvider;
	private final SecureRandom secureRandom;
	private final org.briarproject.briar.api.socialbackup.MessageEncoder
			messageEncoder;

	@Inject
	BackupPayloadEncoderImpl(ClientHelper clientHelper,
			Provider<AuthenticatedCipher> cipherProvider,
			SecureRandom secureRandom,
			org.briarproject.briar.api.socialbackup.MessageEncoder messageEncoder) {
		this.clientHelper = clientHelper;
		this.cipherProvider = cipherProvider;
		this.secureRandom = secureRandom;
		this.messageEncoder = messageEncoder;
	}

	@Override
	public BackupPayload encodeBackupPayload(SecretKey secret,
			Identity identity,
			List<org.briarproject.briar.api.socialbackup.ContactData> contactData,
			int version,
			Map<TransportId, TransportProperties> localTransportProperties) {
		// Encode the local identity
		BdfList bdfIdentity = new BdfList();
		LocalAuthor localAuthor = identity.getLocalAuthor();
		bdfIdentity.add(clientHelper.toList(localAuthor));
		bdfIdentity.add(localAuthor.getPrivateKey().getEncoded());

		// Add handshake keypair
		assert identity.getHandshakePublicKey() != null;
		bdfIdentity.add(identity.getHandshakePublicKey().getEncoded());
		assert identity.getHandshakePrivateKey() != null;
		bdfIdentity.add(identity.getHandshakePrivateKey().getEncoded());

		// Add local transport properties
	    bdfIdentity.add(clientHelper.toDictionary(localTransportProperties));

		// Encode the contact data
		BdfList bdfContactData = new BdfList();
		for (org.briarproject.briar.api.socialbackup.ContactData cd : contactData) {
			BdfList bdfData = new BdfList();
			Contact contact = cd.getContact();
			bdfData.add(clientHelper.toList(contact.getAuthor()));
			bdfData.add(contact.getAlias());
			PublicKey pub = contact.getHandshakePublicKey();
			bdfData.add(pub == null ? null : pub.getEncoded());
			bdfData.add(clientHelper.toDictionary(cd.getProperties()));
			Shard shard = cd.getShard();
			if (shard == null) bdfData.add(null);
			else bdfData.add(messageEncoder.encodeShardMessage(shard));
			bdfContactData.add(bdfData);
		}
		// Encode and encrypt the payload
		BdfList backup = new BdfList();
		backup.add(version);
		backup.add(bdfIdentity);
		backup.add(bdfContactData);
		try {
			byte[] plaintext = clientHelper.toByteArray(backup);
			byte[] ciphertext = new byte[plaintext.length + AUTH_TAG_BYTES];
			byte[] nonce = new byte[NONCE_BYTES];
			secureRandom.nextBytes(nonce);
			AuthenticatedCipher cipher = cipherProvider.get();
			cipher.init(true, secret, nonce);
			int encrypted = cipher.process(plaintext, 0, plaintext.length,
					ciphertext, 0);
			if (encrypted != ciphertext.length) throw new AssertionError();
			byte[] ciphertextWithNonce =
					new byte[ciphertext.length + nonce.length];
			System.arraycopy(nonce, 0, ciphertextWithNonce, 0, nonce.length);
			System.arraycopy(ciphertext, 0, ciphertextWithNonce, nonce.length,
					ciphertext.length);
			return new org.briarproject.briar.api.socialbackup.BackupPayload(
					ciphertextWithNonce);
		} catch (FormatException | GeneralSecurityException e) {
			throw new AssertionError(e);
		}
	}
}
