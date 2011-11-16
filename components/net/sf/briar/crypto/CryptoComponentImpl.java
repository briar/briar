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
	private static final String DIGEST_ALGO = "SHA-256";
	private static final String KEY_PAIR_ALGO = "ECDSA";
	private static final int KEY_PAIR_BITS = 256;
	private static final String FRAME_CIPHER_ALGO = "AES/CTR/NoPadding";
	private static final String SECRET_KEY_ALGO = "AES";
	private static final int SECRET_KEY_BYTES = 32; // 256 bits
	private static final String IV_CIPHER_ALGO = "AES/ECB/NoPadding";
	private static final String MAC_ALGO = "HMacSHA256";
	private static final String SIGNATURE_ALGO = "ECDSA";
	private static final String KEY_DERIVATION_ALGO = "AES/CTR/NoPadding";
	private static final int KEY_DERIVATION_IV_BYTES = 16; // 128 bits

	// Context strings for key derivation
	private static final byte[] FRAME_I = { 'F', 'R', 'A', 'M', 'E', '_', 'I' };
	private static final byte[] FRAME_R = { 'F', 'R', 'A', 'M', 'E', '_', 'R' };
	private static final byte[] IV_I = { 'I', 'V', '_', 'I' };
	private static final byte[] IV_R = { 'I', 'V', '_', 'R' };
	private static final byte[] MAC_I = { 'M', 'A', 'C', '_', 'I' };
	private static final byte[] MAC_R = { 'M', 'A', 'C', '_', 'R' };
	private static final byte[] NEXT = { 'N', 'E', 'X', 'T' };

	private final KeyParser keyParser;
	private final KeyPairGenerator keyPairGenerator;

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
	}

	public ErasableKey deriveFrameKey(byte[] secret, boolean initiator) {
		if(initiator) return deriveKey(secret, FRAME_I);
		else return deriveKey(secret, FRAME_R);
	}

	public ErasableKey deriveIvKey(byte[] secret, boolean initiator) {
		if(initiator) return deriveKey(secret, IV_I);
		else return deriveKey(secret, IV_R);
	}

	public ErasableKey deriveMacKey(byte[] secret, boolean initiator) {
		if(initiator) return deriveKey(secret, MAC_I);
		else return deriveKey(secret, MAC_R);
	}

	private ErasableKey deriveKey(byte[] secret, byte[] context) {
		byte[] key = counterModeKdf(secret, context);
		return new ErasableKeyImpl(key, SECRET_KEY_ALGO);
	}

	// Key derivation function based on a block cipher in CTR mode - see
	// NIST SP 800-108, section 5.1
	private byte[] counterModeKdf(byte[] secret, byte[] context) {
		// The secret must be usable as a key
		if(secret.length != SECRET_KEY_BYTES)
			throw new IllegalArgumentException();
		ErasableKey key = new ErasableKeyImpl(secret, SECRET_KEY_ALGO);
		// The context must leave two bytes free for the length
		if(context.length + 2 > SECRET_KEY_BYTES)
			throw new IllegalArgumentException();
		byte[] input = new byte[SECRET_KEY_BYTES];
		// The input starts with the length of the context as a big-endian int16
		ByteUtils.writeUint16(context.length, input, 0);
		// The remaining bytes of the input are the context
		System.arraycopy(context, 0, input, 2, context.length);
		// Initialise the counter to zero
		byte[] zero = new byte[KEY_DERIVATION_IV_BYTES];
		IvParameterSpec iv = new IvParameterSpec(zero);
		try {
			Cipher cipher = Cipher.getInstance(KEY_DERIVATION_ALGO, PROVIDER);
			cipher.init(Cipher.ENCRYPT_MODE, key, iv);
			byte[] output = cipher.doFinal(input);
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
		byte[] context = new byte[NEXT.length + 6];
		System.arraycopy(NEXT, 0, context, 0, NEXT.length);
		ByteUtils.writeUint16(index, context, NEXT.length);
		ByteUtils.writeUint32(connection, context, NEXT.length + 2);
		return counterModeKdf(secret, context);
	}

	public KeyPair generateKeyPair() {
		return keyPairGenerator.generateKeyPair();
	}

	public ErasableKey generateTestKey() {
		byte[] b = new byte[SECRET_KEY_BYTES];
		getSecureRandom().nextBytes(b);
		return new ErasableKeyImpl(b, SECRET_KEY_ALGO);
	}

	public Cipher getFrameCipher() {
		try {
			return Cipher.getInstance(FRAME_CIPHER_ALGO, PROVIDER);
		} catch(GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public Cipher getIvCipher() {
		try {
			return Cipher.getInstance(IV_CIPHER_ALGO, PROVIDER);
		} catch(GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public KeyParser getKeyParser() {
		return keyParser;
	}

	public Mac getMac() {
		try {
			return Mac.getInstance(MAC_ALGO, PROVIDER);
		} catch(GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
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
		// FIXME: Implement a PRNG (pony/rainbow/nyancat generator)
		return new SecureRandom();
	}

	public Signature getSignature() {
		try {
			return Signature.getInstance(SIGNATURE_ALGO, PROVIDER);
		} catch(GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}
}
