package net.sf.briar.crypto;

import static net.sf.briar.api.plugins.InvitationConstants.CODE_BITS;

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
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.util.ByteUtils;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.google.inject.Inject;

class CryptoComponentImpl implements CryptoComponent {

	private static final String PROVIDER = "BC";
	private static final String AGREEMENT_KEY_PAIR_ALGO = "ECDH";
	private static final int AGREEMENT_KEY_PAIR_BITS = 384;
	private static final String AGREEMENT_ALGO = "ECDHC";
	private static final String SECRET_KEY_ALGO = "AES";
	private static final int SECRET_KEY_BYTES = 32; // 256 bits
	private static final int KEY_DERIVATION_IV_BYTES = 16; // 128 bits
	private static final String KEY_DERIVATION_ALGO = "AES/CTR/NoPadding";
	private static final String DIGEST_ALGO = "SHA-384";
	private static final String SIGNATURE_KEY_PAIR_ALGO = "ECDSA";
	private static final int SIGNATURE_KEY_PAIR_BITS = 384;
	private static final String SIGNATURE_ALGO = "ECDSA";
	private static final String TAG_CIPHER_ALGO = "AES/ECB/NoPadding";
	private static final String FRAME_CIPHER_ALGO = "AES/CTR/NoPadding";
	private static final String MAC_ALGO = "HMacSHA384";

	// Labels for key derivation
	private static final byte[] TAG = { 'T', 'A', 'G' };
	private static final byte[] FRAME = { 'F', 'R', 'A', 'M', 'E' };
	private static final byte[] MAC = { 'M', 'A', 'C' };
	// Labels for secret derivation
	private static final byte[] FIRST = { 'F', 'I', 'R', 'S', 'T' };
	private static final byte[] NEXT = { 'N', 'E', 'X', 'T' };
	// Label for confirmation code derivation
	private static final byte[] CODE = { 'C', 'O', 'D', 'E' };
	// Context strings for key and confirmation code derivation
	private static final byte[] INITIATOR = { 'I' };
	private static final byte[] RESPONDER = { 'R' };
	// Blank plaintext for key derivation
	private static final byte[] KEY_DERIVATION_INPUT =
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

	public ErasableKey deriveTagKey(byte[] secret, boolean initiator) {
		if(initiator) return deriveKey(secret, TAG, INITIATOR);
		else return deriveKey(secret, TAG, RESPONDER);
	}

	public ErasableKey deriveFrameKey(byte[] secret, boolean initiator) {
		if(initiator) return deriveKey(secret, FRAME, INITIATOR);
		else return deriveKey(secret, FRAME, RESPONDER);
	}

	public ErasableKey deriveMacKey(byte[] secret, boolean initiator) {
		if(initiator) return deriveKey(secret, MAC, INITIATOR);
		else return deriveKey(secret, MAC, RESPONDER);
	}

	private ErasableKey deriveKey(byte[] secret, byte[] label, byte[] context) {
		byte[] key = counterModeKdf(secret, label, context);
		return new ErasableKeyImpl(key, SECRET_KEY_ALGO);
	}

	// Key derivation function based on a block cipher in CTR mode - see
	// NIST SP 800-108, section 5.1
	private byte[] counterModeKdf(byte[] secret, byte[] label, byte[] context) {
		// The secret must be usable as a key
		if(secret.length != SECRET_KEY_BYTES)
			throw new IllegalArgumentException();
		// The label and context must leave a byte free for the counter
		if(label.length + context.length + 2 >= KEY_DERIVATION_IV_BYTES)
			throw new IllegalArgumentException();
		// The IV contains the length-prefixed label and context
		byte[] ivBytes = new byte[KEY_DERIVATION_IV_BYTES];
		ByteUtils.writeUint8(label.length, ivBytes, 0);
		System.arraycopy(label, 0, ivBytes, 1, label.length);
		ByteUtils.writeUint8(context.length, ivBytes, label.length + 1);
		System.arraycopy(context, 0, ivBytes, label.length + 2, context.length);
		// Use the secret and the IV to encrypt a blank plaintext
		IvParameterSpec iv = new IvParameterSpec(ivBytes);
		ErasableKey key = new ErasableKeyImpl(secret, SECRET_KEY_ALGO);
		try {
			Cipher cipher = Cipher.getInstance(KEY_DERIVATION_ALGO, PROVIDER);
			cipher.init(Cipher.ENCRYPT_MODE, key, iv);
			byte[] output = cipher.doFinal(KEY_DERIVATION_INPUT);
			assert output.length == SECRET_KEY_BYTES;
			return output;
		} catch(GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public byte[][] deriveInitialSecrets(byte[] ourPublicKey,
			byte[] theirPublicKey, PrivateKey ourPrivateKey, int invitationCode,
			boolean initiator) {
		try {
			PublicKey theirPublic = agreementKeyParser.parsePublicKey(
					theirPublicKey);
			MessageDigest messageDigest = getMessageDigest();
			byte[] ourHash = messageDigest.digest(ourPublicKey);
			byte[] theirHash = messageDigest.digest(theirPublicKey);
			// The initiator and responder info are hashes of the public keys
			byte[] initiatorInfo, responderInfo;
			if(initiator) {
				initiatorInfo = ourHash;
				responderInfo = theirHash;
			} else {
				initiatorInfo = theirHash;
				responderInfo = ourHash;
			}
			// The public info is the invitation code as a uint32
			byte[] publicInfo = new byte[4];
			ByteUtils.writeUint32(invitationCode, publicInfo, 0);
			// The raw secret comes from the key agreement algorithm
			KeyAgreement keyAgreement = KeyAgreement.getInstance(AGREEMENT_ALGO,
					PROVIDER);
			keyAgreement.init(ourPrivateKey);
			keyAgreement.doPhase(theirPublic, true);
			byte[] rawSecret = keyAgreement.generateSecret();
			// Derive the cooked secret from the raw secret using the
			// concatenation KDF
			byte[] cookedSecret = concatenationKdf(rawSecret, FIRST,
					initiatorInfo, responderInfo, publicInfo);
			ByteUtils.erase(rawSecret);
			// Derive the incoming and outgoing secrets from the cooked secret
			// using the CTR mode KDF
			byte[][] secrets = new byte[2][];
			secrets[0] = counterModeKdf(cookedSecret, FIRST, INITIATOR);
			secrets[1] = counterModeKdf(cookedSecret, FIRST, RESPONDER);
			ByteUtils.erase(cookedSecret);
			return secrets;
		} catch(GeneralSecurityException e) {
			return null;
		}
	}

	// Key derivation function based on a hash function - see NIST SP 800-56A,
	// section 5.8
	private byte[] concatenationKdf(byte[] rawSecret, byte[] label,
			byte[] initiatorInfo, byte[] responderInfo, byte[] publicInfo) {
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
		ByteUtils.writeUint8(publicInfo.length, length, 0);
		messageDigest.update(length);
		messageDigest.update(publicInfo);
		byte[] hash = messageDigest.digest();
		// The secret is the first SECRET_KEY_BYTES bytes of the hash
		byte[] output = new byte[SECRET_KEY_BYTES];
		System.arraycopy(hash, 0, output, 0, SECRET_KEY_BYTES);
		ByteUtils.erase(hash);
		return output;
	}

	public byte[] deriveNextSecret(byte[] secret, int index, long connection) {
		if(index < 0 || index > ByteUtils.MAX_16_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		if(connection < 0 || connection > ByteUtils.MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		byte[] context = new byte[6];
		ByteUtils.writeUint16(index, context, 0);
		ByteUtils.writeUint32(connection, context, 2);
		return counterModeKdf(secret, NEXT, context);
	}

	public int deriveConfirmationCode(byte[] secret) {
		byte[] output = counterModeKdf(secret, CODE, CODE);
		int code =  ByteUtils.readUint(output, CODE_BITS);
		ByteUtils.erase(output);
		return code;
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

	public Cipher getFrameCipher() {
		try {
			return Cipher.getInstance(FRAME_CIPHER_ALGO, PROVIDER);
		} catch(GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public Mac getMac() {
		try {
			return Mac.getInstance(MAC_ALGO, PROVIDER);
		} catch(GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}
}
