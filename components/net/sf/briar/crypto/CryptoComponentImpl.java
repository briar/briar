package net.sf.briar.crypto;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.util.ByteUtils;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.google.inject.Inject;

class CryptoComponentImpl implements CryptoComponent {

	private static final String PROVIDER = "BC";
	private static final String KEY_PAIR_ALGO = "ECDSA";
	private static final int KEY_PAIR_BITS = 256;
	private static final String SECRET_KEY_ALGO = "AES";
	private static final int SECRET_KEY_BYTES = 32; // 256 bits
	private static final int KEY_DERIVATION_IV_BYTES = 16; // 128 bits
	private static final String KEY_DERIVATION_ALGO = "AES/CTR/NoPadding";
	private static final String DIGEST_ALGO = "SHA-256";
	private static final String SIGNATURE_ALGO = "ECDSA";
	private static final String TAG_CIPHER_ALGO = "AES/ECB/NoPadding";
	private static final String SEGMENT_CIPHER_ALGO = "AES/CTR/NoPadding";
	private static final String MAC_ALGO = "HMacSHA256";

	// Labels for key derivation, null-terminated
	private static final byte[] TAG = { 'T', 'A', 'G', 0 };
	private static final byte[] SEGMENT = { 'S', 'E', 'G', 0 };
	private static final byte[] MAC = { 'M', 'A', 'C', 0 };
	private static final byte[] NEXT = { 'N', 'E', 'X', 'T', 0 };
	// Context strings for key derivation
	private static final byte[] INITIATOR = { 'I' };
	private static final byte[] RESPONDER = { 'R' };
	// Blank plaintext for key derivation
	private static final byte[] KEY_DERIVATION_INPUT =
		new byte[SECRET_KEY_BYTES];

	private final KeyParser keyParser;
	private final KeyPairGenerator keyPairGenerator;
	private final SecureRandom secureRandom;

	@Inject
	CryptoComponentImpl() {
		Security.addProvider(new BouncyCastleProvider());
		try {
			keyParser = new KeyParserImpl(KEY_PAIR_ALGO, PROVIDER);
			keyPairGenerator = KeyPairGenerator.getInstance(KEY_PAIR_ALGO,
					PROVIDER);
			keyPairGenerator.initialize(KEY_PAIR_BITS);
		} catch(GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
		secureRandom = new SecureRandom();
	}

	public ErasableKey deriveTagKey(byte[] secret, boolean initiator) {
		if(initiator) return deriveKey(secret, TAG, INITIATOR);
		else return deriveKey(secret, TAG, RESPONDER);
	}

	public ErasableKey deriveSegmentKey(byte[] secret, boolean initiator) {
		if(initiator) return deriveKey(secret, SEGMENT, INITIATOR);
		else return deriveKey(secret, SEGMENT, RESPONDER);
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
		ErasableKey key = new ErasableKeyImpl(secret, SECRET_KEY_ALGO);
		// The label and context must leave a byte free for the counter
		if(label.length + context.length + 1 > KEY_DERIVATION_IV_BYTES)
			throw new IllegalArgumentException();
		// The IV starts with the null-terminated label
		byte[] ivBytes = new byte[KEY_DERIVATION_IV_BYTES];
		System.arraycopy(label, 0, ivBytes, 0, label.length);
		// Next comes the context, leaving the last byte free for the counter
		System.arraycopy(context, 0, ivBytes, label.length, context.length);
		assert ivBytes[ivBytes.length - 1] == 0;
		IvParameterSpec iv = new IvParameterSpec(ivBytes);
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

	public KeyPair generateKeyPair() {
		return keyPairGenerator.generateKeyPair();
	}

	public KeyParser getKeyParser() {
		return keyParser;
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

	public Cipher getSegmentCipher() {
		try {
			return Cipher.getInstance(SEGMENT_CIPHER_ALGO, PROVIDER);
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
