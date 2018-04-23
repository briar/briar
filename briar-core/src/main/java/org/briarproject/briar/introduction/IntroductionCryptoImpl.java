package org.briarproject.briar.introduction;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.KeyParser;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.briar.api.client.SessionId;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Map;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.briar.api.introduction.IntroductionConstants.LABEL_ALICE_MAC_KEY;
import static org.briarproject.briar.api.introduction.IntroductionConstants.LABEL_AUTH_MAC;
import static org.briarproject.briar.api.introduction.IntroductionConstants.LABEL_AUTH_NONCE;
import static org.briarproject.briar.api.introduction.IntroductionConstants.LABEL_AUTH_SIGN;
import static org.briarproject.briar.api.introduction.IntroductionConstants.LABEL_BOB_MAC_KEY;
import static org.briarproject.briar.api.introduction.IntroductionConstants.LABEL_MASTER_KEY;
import static org.briarproject.briar.api.introduction.IntroductionConstants.LABEL_SESSION_ID;
import static org.briarproject.briar.api.introduction.IntroductionManager.CLIENT_VERSION;

@Immutable
@NotNullByDefault
class IntroductionCryptoImpl implements IntroductionCrypto {

	private final CryptoComponent crypto;
	private final ClientHelper clientHelper;

	@Inject
	IntroductionCryptoImpl(
			CryptoComponent crypto,
			ClientHelper clientHelper) {
		this.crypto = crypto;
		this.clientHelper = clientHelper;
	}

	@Override
	public SessionId getSessionId(Author introducer, Author alice,
			Author bob) {
		boolean isAlice = isAlice(alice.getId(), bob.getId());
		byte[] hash = crypto.hash(
				LABEL_SESSION_ID,
				introducer.getId().getBytes(),
				isAlice ? alice.getId().getBytes() : bob.getId().getBytes(),
				isAlice ? bob.getId().getBytes() : alice.getId().getBytes()
		);
		return new SessionId(hash);
	}

	@Override
	public KeyPair generateKeyPair() {
		return crypto.generateAgreementKeyPair();
	}

	@Override
	public boolean isAlice(AuthorId alice, AuthorId bob) {
		byte[] a = alice.getBytes();
		byte[] b = bob.getBytes();
		return Bytes.COMPARATOR.compare(new Bytes(a), new Bytes(b)) < 0;
	}

	@Override
	@SuppressWarnings("ConstantConditions")
	public SecretKey deriveMasterKey(IntroduceeSession s, boolean alice)
			throws GeneralSecurityException {
		return deriveMasterKey(s.getEphemeralPublicKey(),
				s.getEphemeralPrivateKey(), s.getRemotePublicKey(), alice);
	}

	SecretKey deriveMasterKey(byte[] publicKey, byte[] privateKey,
			byte[] remotePublicKey, boolean alice)
			throws GeneralSecurityException {
		KeyParser kp = crypto.getAgreementKeyParser();
		PublicKey remoteEphemeralPublicKey = kp.parsePublicKey(remotePublicKey);
		PublicKey ephemeralPublicKey = kp.parsePublicKey(publicKey);
		PrivateKey ephemeralPrivateKey = kp.parsePrivateKey(privateKey);
		KeyPair keyPair = new KeyPair(ephemeralPublicKey, ephemeralPrivateKey);
		return crypto.deriveSharedSecret(
				LABEL_MASTER_KEY,
				remoteEphemeralPublicKey,
				keyPair,
				new byte[] {CLIENT_VERSION},
				alice ? publicKey : remotePublicKey,
				alice ? remotePublicKey : publicKey
		);
	}

	@Override
	public SecretKey deriveMacKey(SecretKey masterKey, boolean alice) {
		return crypto.deriveKey(
				alice ? LABEL_ALICE_MAC_KEY : LABEL_BOB_MAC_KEY,
				masterKey
		);
	}

	@Override
	@SuppressWarnings("ConstantConditions")
	public byte[] mac(SecretKey macKey, IntroduceeSession s,
			AuthorId localAuthorId, boolean alice) throws FormatException {
		return mac(macKey, s.getIntroducer().getId(), localAuthorId,
				s.getRemoteAuthor().getId(), s.getAcceptTimestamp(),
				s.getRemoteAcceptTimestamp(), s.getEphemeralPublicKey(),
				s.getRemotePublicKey(), s.getTransportProperties(),
				s.getRemoteTransportProperties(), alice);
	}

	byte[] mac(SecretKey macKey, AuthorId introducerId,
			AuthorId localAuthorId, AuthorId remoteAuthorId,
			long acceptTimestamp, long remoteAcceptTimestamp,
			byte[] ephemeralPublicKey, byte[] remoteEphemeralPublicKey,
			Map<TransportId, TransportProperties> transportProperties,
			Map<TransportId, TransportProperties> remoteTransportProperties,
			boolean alice) throws FormatException {
		BdfList localInfo = BdfList.of(
				localAuthorId,
				acceptTimestamp,
				ephemeralPublicKey,
				clientHelper.toDictionary(transportProperties)
		);
		BdfList remoteInfo = BdfList.of(
				remoteAuthorId,
				remoteAcceptTimestamp,
				remoteEphemeralPublicKey,
				clientHelper.toDictionary(remoteTransportProperties)
		);
		BdfList macList = BdfList.of(
				introducerId,
				alice ? localInfo : remoteInfo,
				alice ? remoteInfo : localInfo
		);
		return crypto.mac(
				LABEL_AUTH_MAC,
				macKey,
				clientHelper.toByteArray(macList)
		);
	}

	@Override
	@SuppressWarnings("ConstantConditions")
	public void verifyMac(byte[] mac, IntroduceeSession s,
			AuthorId localAuthorId)
			throws GeneralSecurityException, FormatException {
		boolean alice = isAlice(localAuthorId, s.getRemoteAuthor().getId());
		verifyMac(mac, new SecretKey(s.getMasterKey()),
				s.getIntroducer().getId(), localAuthorId,
				s.getRemoteAuthor().getId(), s.getAcceptTimestamp(),
				s.getRemoteAcceptTimestamp(), s.getEphemeralPublicKey(),
				s.getRemotePublicKey(), s.getTransportProperties(),
				s.getRemoteTransportProperties(), !alice);
	}

	void verifyMac(byte[] mac, SecretKey masterKey,
			AuthorId introducerId, AuthorId localAuthorId,
			AuthorId remoteAuthorId, long acceptTimestamp,
			long remoteAcceptTimestamp, byte[] ephemeralPublicKey,
			byte[] remoteEphemeralPublicKey,
			Map<TransportId, TransportProperties> transportProperties,
			Map<TransportId, TransportProperties> remoteTransportProperties,
			boolean alice) throws GeneralSecurityException, FormatException {
		SecretKey macKey = deriveMacKey(masterKey, alice);
		byte[] calculatedMac =
				mac(macKey, introducerId, localAuthorId, remoteAuthorId,
						acceptTimestamp, remoteAcceptTimestamp,
						ephemeralPublicKey, remoteEphemeralPublicKey,
						transportProperties, remoteTransportProperties, !alice);
		if (!Arrays.equals(mac, calculatedMac)) {
			throw new GeneralSecurityException();
		}
	}

	@Override
	public byte[] sign(SecretKey macKey, byte[] privateKey)
			throws GeneralSecurityException {
		return crypto.sign(
				LABEL_AUTH_SIGN,
				getNonce(macKey),
				privateKey
		);
	}

	@Override
	@SuppressWarnings("ConstantConditions")
	public void verifySignature(byte[] signature, IntroduceeSession s,
			AuthorId localAuthorId) throws GeneralSecurityException {
		boolean alice = isAlice(s.getRemoteAuthor().getId(), localAuthorId);
		SecretKey macKey = deriveMacKey(new SecretKey(s.getMasterKey()), alice);
		verifySignature(macKey, s.getRemoteAuthor().getPublicKey(), signature);
	}

	void verifySignature(SecretKey macKey, byte[] publicKey,
			byte[] signature) throws GeneralSecurityException {
		byte[] nonce = getNonce(macKey);
		if (!crypto.verify(LABEL_AUTH_SIGN, nonce, publicKey, signature)) {
			throw new GeneralSecurityException();
		}
	}

	private byte[] getNonce(SecretKey macKey) {
		return crypto.mac(LABEL_AUTH_NONCE, macKey);
	}

}
