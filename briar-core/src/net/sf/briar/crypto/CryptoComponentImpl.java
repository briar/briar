package net.sf.briar.crypto;

import static javax.crypto.Cipher.DECRYPT_MODE;
import static javax.crypto.Cipher.ENCRYPT_MODE;
import static net.sf.briar.api.invitation.InvitationConstants.CODE_BITS;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;
import static net.sf.briar.crypto.P384Constants.P_384_PARAMS;
import static net.sf.briar.crypto.P384Constants.P_384_Q;
import static net.sf.briar.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import net.sf.briar.api.crypto.AuthenticatedCipher;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.KeyPair;
import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.crypto.PrivateKey;
import net.sf.briar.api.crypto.PseudoRandom;
import net.sf.briar.api.crypto.PublicKey;
import net.sf.briar.api.crypto.SecretKey;
import net.sf.briar.api.crypto.Signature;
import net.sf.briar.util.ByteUtils;

import org.spongycastle.crypto.AsymmetricCipherKeyPair;
import org.spongycastle.crypto.BlockCipher;
import org.spongycastle.crypto.CipherParameters;
import org.spongycastle.crypto.Mac;
import org.spongycastle.crypto.agreement.ECDHCBasicAgreement;
import org.spongycastle.crypto.digests.SHA384Digest;
import org.spongycastle.crypto.engines.AESLightEngine;
import org.spongycastle.crypto.generators.ECKeyPairGenerator;
import org.spongycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.spongycastle.crypto.macs.CMac;
import org.spongycastle.crypto.modes.AEADBlockCipher;
import org.spongycastle.crypto.modes.GCMBlockCipher;
import org.spongycastle.crypto.params.ECKeyGenerationParameters;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.util.Strings;

class CryptoComponentImpl implements CryptoComponent {

	private static final int CIPHER_KEY_BYTES = 32; // 256 bits
	private static final int AGREEMENT_KEY_PAIR_BITS = 384;
	private static final int SIGNATURE_KEY_PAIR_BITS = 384;
	private static final int MAC_BYTES = 16; // 128 bits
	private static final int STORAGE_IV_BYTES = 16; // 128 bits
	private static final int PBKDF_SALT_BYTES = 16; // 128 bits
	private static final int PBKDF_ITERATIONS = 1000;

	// Labels for secret derivation
	private static final byte[] MASTER = { 'M', 'A', 'S', 'T', 'E', 'R', '\0' };
	private static final byte[] FIRST = { 'F', 'I', 'R', 'S', 'T', '\0' };
	private static final byte[] ROTATE = { 'R', 'O', 'T', 'A', 'T', 'E', '\0' };
	// Label for confirmation code derivation
	private static final byte[] CODE = { 'C', 'O', 'D', 'E', '\0' };
	// Label for invitation nonce derivation
	private static final byte[] NONCE = { 'N', 'O', 'N', 'C', 'E', '\0' };
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
	// Blank secret for argument validation
	private static final byte[] BLANK_SECRET = new byte[CIPHER_KEY_BYTES];

	private final KeyParser agreementKeyParser, signatureKeyParser;
	private final SecureRandom secureRandom;
	private final ECKeyPairGenerator agreementKeyPairGenerator;
	private final ECKeyPairGenerator signatureKeyPairGenerator;

	CryptoComponentImpl() {
		agreementKeyParser = new Sec1KeyParser(P_384_PARAMS, P_384_Q,
				AGREEMENT_KEY_PAIR_BITS);
		signatureKeyParser = new Sec1KeyParser(P_384_PARAMS, P_384_Q,
				SIGNATURE_KEY_PAIR_BITS);
		secureRandom = new SecureRandom();
		ECKeyGenerationParameters params = new ECKeyGenerationParameters(
				P_384_PARAMS, secureRandom);
		agreementKeyPairGenerator = new ECKeyPairGenerator();
		agreementKeyPairGenerator.init(params);
		signatureKeyPairGenerator = new ECKeyPairGenerator();
		signatureKeyPairGenerator.init(params);
	}

	public SecretKey generateSecretKey() {
		byte[] b = new byte[CIPHER_KEY_BYTES];
		secureRandom.nextBytes(b);
		return new SecretKeyImpl(b);
	}

	public MessageDigest getMessageDigest() {
		return new DoubleDigest(new SHA384Digest());
	}

	public PseudoRandom getPseudoRandom(int seed1, int seed2) {
		return new PseudoRandomImpl(getMessageDigest(), seed1, seed2);
	}

	public SecureRandom getSecureRandom() {
		return secureRandom;
	}

	public Signature getSignature() {
		return new SignatureImpl(secureRandom);
	}

	public KeyPair generateAgreementKeyPair() {
		AsymmetricCipherKeyPair keyPair =
				agreementKeyPairGenerator.generateKeyPair();
		// Return a wrapper that uses the SEC 1 encoding
		ECPublicKeyParameters ecPublicKey =
				(ECPublicKeyParameters) keyPair.getPublic();
		PublicKey publicKey = new Sec1PublicKey(ecPublicKey,
				AGREEMENT_KEY_PAIR_BITS);
		ECPrivateKeyParameters ecPrivateKey =
				(ECPrivateKeyParameters) keyPair.getPrivate();
		PrivateKey privateKey = new Sec1PrivateKey(ecPrivateKey,
				AGREEMENT_KEY_PAIR_BITS);
		return new KeyPair(publicKey, privateKey);
	}

	public KeyParser getAgreementKeyParser() {
		return agreementKeyParser;
	}

	public KeyPair generateSignatureKeyPair() {
		AsymmetricCipherKeyPair keyPair =
				signatureKeyPairGenerator.generateKeyPair();
		// Return a wrapper that uses the SEC 1 encoding
		ECPublicKeyParameters ecPublicKey =
				(ECPublicKeyParameters) keyPair.getPublic();
		PublicKey publicKey = new Sec1PublicKey(ecPublicKey,
				SIGNATURE_KEY_PAIR_BITS);
		ECPrivateKeyParameters ecPrivateKey =
				(ECPrivateKeyParameters) keyPair.getPrivate();
		PrivateKey privateKey = new Sec1PrivateKey(ecPrivateKey,
				SIGNATURE_KEY_PAIR_BITS);
		return new KeyPair(publicKey, privateKey);
	}

	public KeyParser getSignatureKeyParser() {
		return signatureKeyParser;
	}

	public int generateInvitationCode() {
		int codeBytes = (int) Math.ceil(CODE_BITS / 8.0);
		byte[] random = new byte[codeBytes];
		secureRandom.nextBytes(random);
		return ByteUtils.readUint(random, CODE_BITS);
	}

	public int[] deriveConfirmationCodes(byte[] secret) {
		if(secret.length != CIPHER_KEY_BYTES)
			throw new IllegalArgumentException();
		if(Arrays.equals(secret, BLANK_SECRET))
			throw new IllegalArgumentException();
		byte[] alice = counterModeKdf(secret, CODE, 0);
		byte[] bob = counterModeKdf(secret, CODE, 1);
		int[] codes = new int[2];
		codes[0] = ByteUtils.readUint(alice, CODE_BITS);
		codes[1] = ByteUtils.readUint(bob, CODE_BITS);
		ByteUtils.erase(alice);
		ByteUtils.erase(bob);
		return codes;
	}

	public byte[][] deriveInvitationNonces(byte[] secret) {
		if(secret.length != CIPHER_KEY_BYTES)
			throw new IllegalArgumentException();
		if(Arrays.equals(secret, BLANK_SECRET))
			throw new IllegalArgumentException();
		byte[] alice = counterModeKdf(secret, NONCE, 0);
		byte[] bob = counterModeKdf(secret, NONCE, 1);
		return new byte[][] { alice, bob };
	}

	public byte[] deriveMasterSecret(byte[] theirPublicKey,
			KeyPair ourKeyPair, boolean alice) throws GeneralSecurityException {
		MessageDigest messageDigest = getMessageDigest();
		byte[] ourPublicKey = ourKeyPair.getPublic().getEncoded();
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
		PrivateKey ourPriv = ourKeyPair.getPrivate();
		PublicKey theirPub = agreementKeyParser.parsePublicKey(theirPublicKey);
		// The raw secret comes from the key agreement algorithm
		byte[] raw = deriveSharedSecret(ourPriv, theirPub);
		// Derive the cooked secret from the raw secret using the
		// concatenation KDF
		byte[] cooked = concatenationKdf(raw, MASTER, aliceInfo, bobInfo);
		ByteUtils.erase(raw);
		return cooked;
	}

	// Package access for testing
	byte[] deriveSharedSecret(PrivateKey priv, PublicKey pub)
			throws GeneralSecurityException {
		if(!(priv instanceof Sec1PrivateKey))
			throw new IllegalArgumentException();
		if(!(pub instanceof Sec1PublicKey))
			throw new IllegalArgumentException();
		ECPrivateKeyParameters ecPriv = ((Sec1PrivateKey) priv).getKey();
		ECPublicKeyParameters ecPub = ((Sec1PublicKey) pub).getKey();
		ECDHCBasicAgreement agreement = new ECDHCBasicAgreement();
		agreement.init(ecPriv);
		// FIXME: Should we use another format for the shared secret?
		return agreement.calculateAgreement(ecPub).toByteArray();
	}

	public byte[] deriveInitialSecret(byte[] secret, int transportIndex) {
		if(secret.length != CIPHER_KEY_BYTES)
			throw new IllegalArgumentException();
		if(Arrays.equals(secret, BLANK_SECRET))
			throw new IllegalArgumentException();
		if(transportIndex < 0) throw new IllegalArgumentException();
		return counterModeKdf(secret, FIRST, transportIndex);
	}

	public byte[] deriveNextSecret(byte[] secret, long period) {
		if(secret.length != CIPHER_KEY_BYTES)
			throw new IllegalArgumentException();
		if(Arrays.equals(secret, BLANK_SECRET))
			throw new IllegalArgumentException();
		if(period < 0 || period > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		return counterModeKdf(secret, ROTATE, period);
	}

	public SecretKey deriveTagKey(byte[] secret, boolean alice) {
		if(secret.length != CIPHER_KEY_BYTES)
			throw new IllegalArgumentException();
		if(Arrays.equals(secret, BLANK_SECRET))
			throw new IllegalArgumentException();
		if(alice) return deriveKey(secret, A_TAG, 0);
		else return deriveKey(secret, B_TAG, 0);
	}

	public SecretKey deriveFrameKey(byte[] secret, long connection,
			boolean alice, boolean initiator) {
		if(secret.length != CIPHER_KEY_BYTES)
			throw new IllegalArgumentException();
		if(Arrays.equals(secret, BLANK_SECRET))
			throw new IllegalArgumentException();
		if(connection < 0 || connection > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		if(alice) {
			if(initiator) return deriveKey(secret, A_FRAME_A, connection);
			else return deriveKey(secret, A_FRAME_B, connection);
		} else {
			if(initiator) return deriveKey(secret, B_FRAME_A, connection);
			else return deriveKey(secret, B_FRAME_B, connection);
		}
	}

	private SecretKey deriveKey(byte[] secret, byte[] label, long context) {
		if(secret.length != CIPHER_KEY_BYTES)
			throw new IllegalArgumentException();
		if(Arrays.equals(secret, BLANK_SECRET))
			throw new IllegalArgumentException();
		byte[] key = counterModeKdf(secret, label, context);
		return new SecretKeyImpl(key);
	}

	public AuthenticatedCipher getFrameCipher() {
		AEADBlockCipher cipher = new GCMBlockCipher(new AESLightEngine());
		return new AuthenticatedCipherImpl(cipher, MAC_BYTES);
	}

	public void encodeTag(byte[] tag, SecretKey tagKey, long connection) {
		if(tag.length < TAG_LENGTH) throw new IllegalArgumentException();
		if(connection < 0 || connection > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		for(int i = 0; i < TAG_LENGTH; i++) tag[i] = 0;
		ByteUtils.writeUint32(connection, tag, 0);
		BlockCipher cipher = new AESLightEngine();
		assert cipher.getBlockSize() == TAG_LENGTH;
		KeyParameter k = new KeyParameter(tagKey.getEncoded());
		cipher.init(true, k);
		cipher.processBlock(tag, 0, tag, 0);
		ByteUtils.erase(k.getKey());
	}

	public byte[] encryptWithPassword(byte[] input, char[] password) {
		// Generate a random salt
		byte[] salt = new byte[PBKDF_SALT_BYTES];
		secureRandom.nextBytes(salt);
		// Derive the key from the password
		byte[] keyBytes = pbkdf2(password, salt);
		SecretKey key = new SecretKeyImpl(keyBytes);
		// Generate a random IV
		byte[] iv = new byte[STORAGE_IV_BYTES];
		secureRandom.nextBytes(iv);
		// The output contains the salt, IV, ciphertext and MAC
		int outputLen = salt.length + iv.length + input.length + MAC_BYTES;
		byte[] output = new byte[outputLen];
		System.arraycopy(salt, 0, output, 0, salt.length);
		System.arraycopy(iv, 0, output, salt.length, iv.length);
		// Initialise the cipher and encrypt the plaintext
		try {
			AEADBlockCipher c = new GCMBlockCipher(new AESLightEngine());
			AuthenticatedCipher cipher = new AuthenticatedCipherImpl(c,
					MAC_BYTES);
			cipher.init(ENCRYPT_MODE, key, iv, null);
			int outputOff = salt.length + iv.length;
			cipher.doFinal(input, 0, input.length, output, outputOff);
			return output;
		} catch(GeneralSecurityException e) {
			throw new RuntimeException(e);
		} finally {
			key.erase();
		}
	}

	public byte[] decryptWithPassword(byte[] input, char[] password) {
		// The input contains the salt, IV, ciphertext and MAC
		if(input.length < PBKDF_SALT_BYTES + STORAGE_IV_BYTES + MAC_BYTES)
			return null; // Invalid
		byte[] salt = new byte[PBKDF_SALT_BYTES];
		System.arraycopy(input, 0, salt, 0, salt.length);
		byte[] iv = new byte[STORAGE_IV_BYTES];
		System.arraycopy(input, salt.length, iv, 0, iv.length);
		// Derive the key from the password
		byte[] keyBytes = pbkdf2(password, salt);
		SecretKey key = new SecretKeyImpl(keyBytes);
		// Initialise the cipher
		AuthenticatedCipher cipher;
		try {
			AEADBlockCipher c = new GCMBlockCipher(new AESLightEngine());
			cipher = new AuthenticatedCipherImpl(c, MAC_BYTES);
			cipher.init(DECRYPT_MODE, key, iv, null);
		} catch(GeneralSecurityException e) {
			key.erase();
			throw new RuntimeException(e);
		}
		// Try to decrypt the ciphertext (may be invalid)
		try {
			int inputOff = salt.length + iv.length;
			int inputLen = input.length - salt.length - iv.length;
			byte[] output = new byte[inputLen - MAC_BYTES];
			cipher.doFinal(input, inputOff, inputLen, output, 0);
			return output;
		} catch(GeneralSecurityException e) {
			return null; // Invalid
		} finally {
			key.erase();
		}
	}

	// Key derivation function based on a hash function - see NIST SP 800-56A,
	// section 5.8
	private byte[] concatenationKdf(byte[] rawSecret, byte[] label,
			byte[] initiatorInfo, byte[] responderInfo) {
		// The output of the hash function must be long enough to use as a key
		MessageDigest messageDigest = getMessageDigest();
		if(messageDigest.getDigestLength() < CIPHER_KEY_BYTES)
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
		byte[] output = new byte[CIPHER_KEY_BYTES];
		System.arraycopy(hash, 0, output, 0, output.length);
		ByteUtils.erase(hash);
		return output;
	}

	// Key derivation function based on a PRF in counter mode - see
	// NIST SP 800-108, section 5.1
	private byte[] counterModeKdf(byte[] secret, byte[] label, long context) {
		if(secret.length != CIPHER_KEY_BYTES)
			throw new IllegalArgumentException();
		if(Arrays.equals(secret, BLANK_SECRET))
			throw new IllegalArgumentException();
		if(label[label.length - 1] != '\0')
			throw new IllegalArgumentException();
		Mac prf = new CMac(new AESLightEngine());
		KeyParameter k = new KeyParameter(secret);
		prf.init(k);
		int macLength = prf.getMacSize();
		byte[] mac = new byte[macLength], output = new byte[CIPHER_KEY_BYTES];
		byte[] contextBytes = new byte[4];
		ByteUtils.writeUint32(context, contextBytes, 0);
		for(int i = 0; i * macLength < CIPHER_KEY_BYTES; i++) {
			prf.update((byte) i); // Counter
			prf.update(label, 0, label.length);
			prf.update(contextBytes, 0, contextBytes.length);
			prf.update((byte) CIPHER_KEY_BYTES); // Output length
			prf.doFinal(mac, 0);
			int bytesNeeded = CIPHER_KEY_BYTES - i * macLength;
			int bytesToUse = Math.min(bytesNeeded, macLength);
			System.arraycopy(mac, 0, output, i * macLength, bytesToUse);
			ByteUtils.erase(mac);
		}
		ByteUtils.erase(k.getKey());
		return output;
	}

	// Password-based key derivation function - see PKCS#5 v2.1, section 5.2
	private byte[] pbkdf2(char[] password, byte[] salt) {
		byte[] utf8 = toUtf8ByteArray(password);
		PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator();
		gen.init(utf8, salt, PBKDF_ITERATIONS);
		int keyLengthInBits = CIPHER_KEY_BYTES * 8;
		CipherParameters p = gen.generateDerivedParameters(keyLengthInBits);
		ByteUtils.erase(utf8);
		return ((KeyParameter) p).getKey();
	}

	byte[] toUtf8ByteArray(char[] c) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			Strings.toUTF8ByteArray(c, out);
			byte[] utf8 = out.toByteArray();
			// Erase the output stream's buffer
			out.reset();
			out.write(new byte[utf8.length]);
			return utf8;
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}
}
