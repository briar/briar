package net.sf.briar.crypto;

import static javax.crypto.Cipher.ENCRYPT_MODE;
import static net.sf.briar.api.plugins.InvitationConstants.CODE_BITS;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.api.crypto.AuthenticatedCipher;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.util.ByteUtils;

import org.spongycastle.crypto.engines.AESEngine;
import org.spongycastle.crypto.modes.AEADBlockCipher;
import org.spongycastle.crypto.modes.GCMBlockCipher;
import org.spongycastle.jce.provider.BouncyCastleProvider;

import com.google.inject.Inject;

class CryptoComponentImpl implements CryptoComponent {

	private static final String PROVIDER = "SC";
	private static final String AGREEMENT_KEY_PAIR_ALGO = "ECDH";
	private static final int AGREEMENT_KEY_PAIR_BITS = 384;
	private static final String AGREEMENT_ALGO = "ECDHC";
	private static final String SECRET_KEY_ALGO = "AES";
	private static final int SECRET_KEY_BYTES = 32; // 256 bits
	private static final String KEY_DERIVATION_ALGO = "AES/CTR/NoPadding";
	private static final int KEY_DERIVATION_IV_BYTES = 16; // 128 bits
	private static final String DIGEST_ALGO = "SHA-384";
	private static final String SIGNATURE_KEY_PAIR_ALGO = "ECDSA";
	private static final int SIGNATURE_KEY_PAIR_BITS = 384;
	private static final String SIGNATURE_ALGO = "ECDSA";
	private static final String TAG_CIPHER_ALGO = "AES/ECB/NoPadding";
	private static final int GCM_MAC_LENGTH = 16; // 128 bits

	// Labels for key derivation
	private static final byte[] A_TAG = { 'A', '_', 'T', 'A', 'G', '\0' };
	private static final byte[] B_TAG = { 'B', '_', 'T', 'A', 'G', '\0' };
	private static final byte[] A_FRAME_A =
		{ 'A', '_', 'F', 'R', 'A', 'M', 'E', '_', 'A', '\0' };
	private static final byte[] A_FRAME_B =
		{ 'A', '_', 'F', 'R', 'A', 'M', 'E', '_', 'B', '\0' };
	private static final byte[] B_FRAME_A =
		{ 'B', '_', 'F', 'R', 'A', 'M', 'E', '_', 'A', '\0' };
	private static final byte[] B_FRAME_B =
		{ 'B', '_', 'F', 'R', 'A', 'M', 'E', '_', 'B', '\0' };
	// Labels for secret derivation
	private static final byte[] FIRST = { 'F', 'I', 'R', 'S', 'T', '\0' };
	private static final byte[] ROTATE = { 'R', 'O', 'T', 'A', 'T', 'E', '\0' };
	// Label for confirmation code derivation
	private static final byte[] CODE = { 'C', 'O', 'D', 'E', '\0' };
	// Blank plaintext for key derivation
	private static final byte[] KEY_DERIVATION_BLANK_PLAINTEXT =
			new byte[SECRET_KEY_BYTES];

	private final KeyParser agreementKeyParser, signatureKeyParser;
	private final KeyPairGenerator agreementKeyPairGenerator;
	private final KeyPairGenerator signatureKeyPairGenerator;
	private final SecureRandom secureRandom;

	@Inject
	CryptoComponentImpl() {
		Security.addProvider(new BouncyCastleProvider());
		try {
			agreementKeyParser = new KeyParserImpl(AGREEMENT_KEY_PAIR_ALGO,
					PROVIDER);
			signatureKeyParser = new KeyParserImpl(SIGNATURE_KEY_PAIR_ALGO,
					PROVIDER);
			agreementKeyPairGenerator = KeyPairGenerator.getInstance(
					AGREEMENT_KEY_PAIR_ALGO, PROVIDER);
			agreementKeyPairGenerator.initialize(AGREEMENT_KEY_PAIR_BITS);
			signatureKeyPairGenerator = KeyPairGenerator.getInstance(
					SIGNATURE_KEY_PAIR_ALGO, PROVIDER);
			signatureKeyPairGenerator.initialize(SIGNATURE_KEY_PAIR_BITS);
		} catch(GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
		secureRandom = new SecureRandom();
	}

	public ErasableKey deriveTagKey(byte[] secret, boolean alice) {
		if(alice) return deriveKey(secret, A_TAG, 0L);
		else return deriveKey(secret, B_TAG, 0L);
	}

	public ErasableKey deriveFrameKey(byte[] secret, long connection,
			boolean alice, boolean initiator) {
		if(alice) {
			if(initiator) return deriveKey(secret, A_FRAME_A, connection);
			else return deriveKey(secret, A_FRAME_B, connection);
		} else {
			if(initiator) return deriveKey(secret, B_FRAME_A, connection);
			else return deriveKey(secret, B_FRAME_B, connection);
		}
	}

	private ErasableKey deriveKey(byte[] secret, byte[] label, long context) {
		byte[] key = counterModeKdf(secret, label, context);
		return new ErasableKeyImpl(key, SECRET_KEY_ALGO);
	}

	// Key derivation function based on a block cipher in CTR mode - see
	// NIST SP 800-108, section 5.1
	private byte[] counterModeKdf(byte[] secret, byte[] label, long context) {
		// The secret must be usable as a key
		if(secret.length != SECRET_KEY_BYTES)
			throw new IllegalArgumentException();
		// The label and context must leave a byte free for the counter
		if(label.length + 4 >= KEY_DERIVATION_IV_BYTES)
			throw new IllegalArgumentException();
		byte[] ivBytes = new byte[KEY_DERIVATION_IV_BYTES];
		System.arraycopy(label, 0, ivBytes, 0, label.length);
		ByteUtils.writeUint32(context, ivBytes, label.length);
		// Use the secret and the IV to encrypt a blank plaintext
		IvParameterSpec iv = new IvParameterSpec(ivBytes);
		ErasableKey key = new ErasableKeyImpl(secret, SECRET_KEY_ALGO);
		try {
			Cipher cipher = Cipher.getInstance(KEY_DERIVATION_ALGO, PROVIDER);
			cipher.init(Cipher.ENCRYPT_MODE, key, iv);
			byte[] output = cipher.doFinal(KEY_DERIVATION_BLANK_PLAINTEXT);
			assert output.length == SECRET_KEY_BYTES;
			return output;
		} catch(GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public byte[] deriveInitialSecret(byte[] ourPublicKey,
			byte[] theirPublicKey, PrivateKey ourPrivateKey, boolean alice) {
		try {
			PublicKey theirPublic = agreementKeyParser.parsePublicKey(
					theirPublicKey);
			MessageDigest messageDigest = getMessageDigest();
			byte[] ourHash = messageDigest.digest(ourPublicKey);
			byte[] theirHash = messageDigest.digest(theirPublicKey);
			byte[] aliceInfo, bobInfo;
			if(alice) {
				aliceInfo = ourHash;
				bobInfo = theirHash;
			} else {
				aliceInfo = theirHash;
				bobInfo = ourHash;
			}
			// The raw secret comes from the key agreement algorithm
			KeyAgreement keyAgreement = KeyAgreement.getInstance(AGREEMENT_ALGO,
					PROVIDER);
			keyAgreement.init(ourPrivateKey);
			keyAgreement.doPhase(theirPublic, true);
			byte[] rawSecret = keyAgreement.generateSecret();
			// Derive the cooked secret from the raw secret using the
			// concatenation KDF
			byte[] cookedSecret = concatenationKdf(rawSecret, FIRST, aliceInfo,
					bobInfo);
			ByteUtils.erase(rawSecret);
			return cookedSecret;
		} catch(GeneralSecurityException e) {
			// FIXME: Throw instead of returning null?
			return null;
		}
	}

	// Key derivation function based on a hash function - see NIST SP 800-56A,
	// section 5.8
	private byte[] concatenationKdf(byte[] rawSecret, byte[] label,
			byte[] initiatorInfo, byte[] responderInfo) {
		// The output of the hash function must be long enough to use as a key
		MessageDigest messageDigest = getMessageDigest();
		if(messageDigest.getDigestLength() < SECRET_KEY_BYTES)
			throw new RuntimeException();
		// All fields are length-prefixed
		byte[] length = new byte[1];
		ByteUtils.writeUint8(rawSecret.length, length, 0);
		messageDigest.update(length);
		messageDigest.update(rawSecret);
		ByteUtils.writeUint8(label.length, length, 0);
		messageDigest.update(length);
		messageDigest.update(label);
		ByteUtils.writeUint8(initiatorInfo.length, length, 0);
		messageDigest.update(length);
		messageDigest.update(initiatorInfo);
		ByteUtils.writeUint8(responderInfo.length, length, 0);
		messageDigest.update(length);
		messageDigest.update(responderInfo);
		byte[] hash = messageDigest.digest();
		// The secret is the first SECRET_KEY_BYTES bytes of the hash
		byte[] output = new byte[SECRET_KEY_BYTES];
		System.arraycopy(hash, 0, output, 0, SECRET_KEY_BYTES);
		ByteUtils.erase(hash);
		return output;
	}

	public byte[] deriveNextSecret(byte[] secret, long period) {
		if(period < 0 || period > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		return counterModeKdf(secret, ROTATE, period);
	}

	public void encodeTag(byte[] tag, Cipher tagCipher, ErasableKey tagKey,
			long connection) {
		if(tag.length < TAG_LENGTH) throw new IllegalArgumentException();
		if(connection < 0 || connection > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		for(int i = 0; i < TAG_LENGTH; i++) tag[i] = 0;
		ByteUtils.writeUint32(connection, tag, 0);
		try {
			tagCipher.init(ENCRYPT_MODE, tagKey);
			int encrypted = tagCipher.doFinal(tag, 0, TAG_LENGTH, tag);
			if(encrypted != TAG_LENGTH) throw new IllegalArgumentException();
		} catch(GeneralSecurityException e) {
			// Unsuitable cipher or key
			throw new IllegalArgumentException(e);
		}
	}

	public int generateInvitationCode() {
		int codeBytes = (int) Math.ceil(CODE_BITS / 8.0);
		byte[] random = new byte[codeBytes];
		secureRandom.nextBytes(random);
		return ByteUtils.readUint(random, CODE_BITS);
	}

	public int[] deriveConfirmationCodes(byte[] secret) {
		byte[] alice = counterModeKdf(secret, CODE, 0);
		byte[] bob = counterModeKdf(secret, CODE, 1);
		int[] codes = new int[2];
		codes[0] = ByteUtils.readUint(alice, CODE_BITS);
		codes[1] = ByteUtils.readUint(bob, CODE_BITS);
		ByteUtils.erase(alice);
		ByteUtils.erase(bob);
		return codes;
	}

	public KeyPair generateAgreementKeyPair() {
		return agreementKeyPairGenerator.generateKeyPair();
	}

	public KeyPair generateSignatureKeyPair() {
		return signatureKeyPairGenerator.generateKeyPair();
	}

	public KeyParser getSignatureKeyParser() {
		return signatureKeyParser;
	}

	public ErasableKey generateTestKey() {
		byte[] b = new byte[SECRET_KEY_BYTES];
		getSecureRandom().nextBytes(b);
		return new ErasableKeyImpl(b, SECRET_KEY_ALGO);
	}

	public MessageDigest getMessageDigest() {
		try {
			return new DoubleDigest(java.security.MessageDigest.getInstance(
					DIGEST_ALGO, PROVIDER));
		} catch(GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public PseudoRandom getPseudoRandom(int seed) {
		return new PseudoRandomImpl(getMessageDigest(), seed);
	}

	public SecureRandom getSecureRandom() {
		return secureRandom;
	}

	public Signature getSignature() {
		try {
			return Signature.getInstance(SIGNATURE_ALGO, PROVIDER);
		} catch(GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public Cipher getTagCipher() {
		try {
			return Cipher.getInstance(TAG_CIPHER_ALGO, PROVIDER);
		} catch(GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public AuthenticatedCipher getFrameCipher() {
		// This code is specific to BouncyCastle because javax.crypto.Cipher
		// doesn't support additional authenticated data until Java 7
		AEADBlockCipher cipher = new GCMBlockCipher(new AESEngine());
		return new AuthenticatedCipherImpl(cipher, GCM_MAC_LENGTH);
	}
}
