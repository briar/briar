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

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import javax.inject.Provider;

import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.AUTH_TAG_BYTES;
import static org.briarproject.briar.socialbackup.SocialBackupConstants.NONCE_BYTES;

@Immutable
@NotNullByDefault
class BackupPayloadEncoderImpl implements BackupPayloadEncoder {

	private final ClientHelper clientHelper;
	private final Provider<AuthenticatedCipher> cipherProvider;
	private final SecureRandom secureRandom;

	@Inject
	BackupPayloadEncoderImpl(ClientHelper clientHelper,
			Provider<AuthenticatedCipher> cipherProvider,
			SecureRandom secureRandom) {
		this.clientHelper = clientHelper;
		this.cipherProvider = cipherProvider;
		this.secureRandom = secureRandom;
	}

	@Override
	public BackupPayload encodeBackupPayload(SecretKey secret,
			Identity identity, List<Contact> contacts,
			List<Map<TransportId, TransportProperties>> properties,
			int version) {
		if (contacts.size() != properties.size()) {
			throw new IllegalArgumentException();
		}
		// Encode the local identity
		BdfList identityData = new BdfList();
		LocalAuthor localAuthor = identity.getLocalAuthor();
		identityData.add(clientHelper.toList(localAuthor));
		identityData.add(localAuthor.getPrivateKey().getEncoded());
		identityData.add(identity.getHandshakePublicKey().getEncoded());
		identityData.add(identity.getHandshakePrivateKey().getEncoded());
		// Encode the contacts
		BdfList contactData = new BdfList();
		for (int i = 0; i < contacts.size(); i++) {
			Contact contact = contacts.get(i);
			Map<TransportId, TransportProperties> props = properties.get(i);
			BdfList data = new BdfList();
			data.add(clientHelper.toList(contact.getAuthor()));
			data.add(contact.getAlias());
			PublicKey pub = requireNonNull(contact.getHandshakePublicKey());
			data.add(pub.getEncoded());
			data.add(clientHelper.toDictionary(props));
			contactData.add(data);
		}
		// Encode and encrypt the payload
		BdfList backup = new BdfList();
		backup.add(version);
		backup.add(identityData);
		backup.add(contactData);
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
			return new BackupPayload(ciphertext);
		} catch (FormatException | GeneralSecurityException e) {
			throw new AssertionError(e);
		}
	}
}
