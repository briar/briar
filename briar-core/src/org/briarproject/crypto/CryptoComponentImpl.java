package org.briarproject.crypto;

import static java.util.logging.Level.INFO;
import static org.briarproject.api.invitation.InvitationConstants.CODE_BITS;
import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;
import static org.briarproject.crypto.EllipticCurveConstants.PARAMETERS;
import static org.briarproject.util.ByteUtils.MAX_32_BIT_UNSIGNED;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyPair;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.MessageDigest;
import org.briarproject.api.crypto.PrivateKey;
import org.briarproject.api.crypto.PseudoRandom;
import org.briarproject.api.crypto.PublicKey;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.crypto.Signature;
import org.briarproject.api.system.SeedProvider;
import org.briarproject.util.ByteUtils;
import org.briarproject.util.StringUtils;
import org.spongycastle.crypto.AsymmetricCipherKeyPair;
import org.spongycastle.crypto.BlockCipher;
import org.spongycastle.crypto.CipherParameters;
import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.Mac;
import org.spongycastle.crypto.agreement.ECDHCBasicAgreement;
import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.crypto.engines.AESLightEngine;
import org.spongycastle.crypto.generators.ECKeyPairGenerator;
import org.spongycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.spongycastle.crypto.macs.HMac;
import org.spongycastle.crypto.params.ECKeyGenerationParameters;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.spongycastle.crypto.params.KeyParameter;

class CryptoComponentImpl implements CryptoComponent {

	private static final Logger LOG =
			Logger.getLogger(CryptoComponentImpl.class.getName());

	private static final int AGREEMENT_KEY_PAIR_BITS = 256;
	private static final int SIGNATURE_KEY_PAIR_BITS = 256;
	private static final int STORAGE_IV_BYTES = 16; // 128 bits
	private static final int PBKDF_SALT_BYTES = 16; // 128 bits
	private static final int PBKDF_TARGET_MILLIS = 500;
	private static final int PBKDF_SAMPLES = 30;

	// Labels for secret derivation
	private static final byte[] MASTER = { 'M', 'A', 'S', 'T', 'E', 'R', '\0' };
	private static final byte[] SALT = { 'S', 'A', 'L', 'T', '\0' };
	private static final byte[] FIRST = { 'F', 'I', 'R', 'S', 'T', '\0' };
	private static final byte[] ROTATE = { 'R', 'O', 'T', 'A', 'T', 'E', '\0' };
	// Label for confirmation code derivation
	private static final byte[] CODE = { 'C', 'O', 'D', 'E', '\0' };
	// Label for invitation nonce derivation
	private static final byte[] NONCE = { 'N', 'O', 'N', 'C', 'E', '\0' };
	// Labels for key derivation
	private static final byte[] A_TAG = { 'A', '_', 'T', 'A', 'G', '\0' };
	private static final byte[] B_TAG = { 'B', '_', 'T', 'A', 'G', '\0' };
	private static final byte[] A_FRAME =
		{ 'A', '_', 'F', 'R', 'A', 'M', 'E', '\0' };
	private static final byte[] B_FRAME =
		{ 'B', '_', 'F', 'R', 'A', 'M', 'E', '\0' };

	private final SecureRandom secureRandom;
	private final ECKeyPairGenerator agreementKeyPairGenerator;
	private final ECKeyPairGenerator signatureKeyPairGenerator;
	private final KeyParser agreementKeyParser, signatureKeyParser;

	@Inject
	CryptoComponentImpl(SeedProvider r) {
		if (!FortunaSecureRandom.selfTest()) throw new RuntimeException();
		SecureRandom secureRandom1 = new SecureRandom();
		if (LOG.isLoggable(INFO)) {
			String provider = secureRandom1.getProvider().getName();
			String algorithm = secureRandom1.getAlgorithm();
			LOG.info("Default SecureRandom: " + provider + " " + algorithm);
		}
		SecureRandom secureRandom2 = new FortunaSecureRandom(r.getSeed());
		secureRandom = new CombinedSecureRandom(secureRandom1, secureRandom2);
		ECKeyGenerationParameters params = new ECKeyGenerationParameters(
				PARAMETERS, secureRandom);
		agreementKeyPairGenerator = new ECKeyPairGenerator();
		agreementKeyPairGenerator.init(params);
		signatureKeyPairGenerator = new ECKeyPairGenerator();
		signatureKeyPairGenerator.init(params);
		agreementKeyParser = new Sec1KeyParser(PARAMETERS,
				AGREEMENT_KEY_PAIR_BITS);
		signatureKeyParser = new Sec1KeyParser(PARAMETERS,
				SIGNATURE_KEY_PAIR_BITS);
	}

	public SecretKey generateSecretKey() {
		byte[] b = new byte[SecretKey.LENGTH];
		secureRandom.nextBytes(b);
		return new SecretKey(b);
	}

	public MessageDigest getMessageDigest() {
		return new DoubleDigest(new SHA256Digest());
	}

	public PseudoRandom getPseudoRandom(int seed1, int seed2) {
		return new PseudoRandomImpl(seed1, seed2);
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
		int codeBytes = (CODE_BITS + 7) / 8;
		byte[] random = new byte[codeBytes];
		secureRandom.nextBytes(random);
		return ByteUtils.readUint(random, CODE_BITS);
	}

	public int[] deriveConfirmationCodes(byte[] secret) {
		if (secret.length != SecretKey.LENGTH)
			throw new IllegalArgumentException();
		byte[] alice = counterModeKdf(secret, CODE, 0);
		byte[] bob = counterModeKdf(secret, CODE, 1);
		int[] codes = new int[2];
		codes[0] = ByteUtils.readUint(alice, CODE_BITS);
		codes[1] = ByteUtils.readUint(bob, CODE_BITS);
		return codes;
	}

	public byte[][] deriveInvitationNonces(byte[] secret) {
		if (secret.length != SecretKey.LENGTH)
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
		if (alice) {
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
		return concatenationKdf(raw, MASTER, aliceInfo, bobInfo);
	}

	// Package access for testing
	byte[] deriveSharedSecret(PrivateKey priv, PublicKey pub)
			throws GeneralSecurityException {
		if (!(priv instanceof Sec1PrivateKey))
			throw new IllegalArgumentException();
		if (!(pub instanceof Sec1PublicKey))
			throw new IllegalArgumentException();
		ECPrivateKeyParameters ecPriv = ((Sec1PrivateKey) priv).getKey();
		ECPublicKeyParameters ecPub = ((Sec1PublicKey) pub).getKey();
		long now = System.currentTimeMillis();
		ECDHCBasicAgreement agreement = new ECDHCBasicAgreement();
		agreement.init(ecPriv);
		byte[] secret = agreement.calculateAgreement(ecPub).toByteArray();
		long duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Deriving shared secret took " + duration + " ms");
		return secret;
	}

	public byte[] deriveGroupSalt(byte[] secret) {
		if (secret.length != SecretKey.LENGTH)
			throw new IllegalArgumentException();
		return counterModeKdf(secret, SALT, 0);
	}

	public byte[] deriveInitialSecret(byte[] secret, int transportIndex) {
		if (secret.length != SecretKey.LENGTH)
			throw new IllegalArgumentException();
		if (transportIndex < 0) throw new IllegalArgumentException();
		return counterModeKdf(secret, FIRST, transportIndex);
	}

	public byte[] deriveNextSecret(byte[] secret, long period) {
		if (secret.length != SecretKey.LENGTH)
			throw new IllegalArgumentException();
		if (period < 0 || period > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		return counterModeKdf(secret, ROTATE, period);
	}

	public SecretKey deriveTagKey(byte[] secret, boolean alice) {
		if (secret.length != SecretKey.LENGTH)
			throw new IllegalArgumentException();
		if (alice) return deriveKey(secret, A_TAG, 0);
		else return deriveKey(secret, B_TAG, 0);
	}

	public SecretKey deriveFrameKey(byte[] secret, long streamNumber,
			boolean alice) {
		if (secret.length != SecretKey.LENGTH)
			throw new IllegalArgumentException();
		if (streamNumber < 0 || streamNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		if (alice) return deriveKey(secret, A_FRAME, streamNumber);
		else return deriveKey(secret, B_FRAME, streamNumber);
	}

	private SecretKey deriveKey(byte[] secret, byte[] label, long context) {
		return new SecretKey(counterModeKdf(secret, label, context));
	}

	public void encodeTag(byte[] tag, SecretKey tagKey, long streamNumber) {
		if (tag.length < TAG_LENGTH) throw new IllegalArgumentException();
		if (streamNumber < 0 || streamNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		for (int i = 0; i < TAG_LENGTH; i++) tag[i] = 0;
		ByteUtils.writeUint32(streamNumber, tag, 0);
		BlockCipher cipher = new AESLightEngine();
		assert cipher.getBlockSize() == TAG_LENGTH;
		KeyParameter k = new KeyParameter(tagKey.getBytes());
		cipher.init(true, k);
		cipher.processBlock(tag, 0, tag, 0);
	}

	public byte[] encryptWithPassword(byte[] input, String password) {
		AuthenticatedCipher cipher = new AuthenticatedCipherImpl();
		int macBytes = cipher.getMacBytes();
		// Generate a random salt
		byte[] salt = new byte[PBKDF_SALT_BYTES];
		secureRandom.nextBytes(salt);
		// Calibrate the KDF
		int iterations = chooseIterationCount(PBKDF_TARGET_MILLIS);
		// Derive the key from the password
		SecretKey key = new SecretKey(pbkdf2(password, salt, iterations));
		// Generate a random IV
		byte[] iv = new byte[STORAGE_IV_BYTES];
		secureRandom.nextBytes(iv);
		// The output contains the salt, iterations, IV, ciphertext and MAC
		int outputLen = salt.length + 4 + iv.length + input.length + macBytes;
		byte[] output = new byte[outputLen];
		System.arraycopy(salt, 0, output, 0, salt.length);
		ByteUtils.writeUint32(iterations, output, salt.length);
		System.arraycopy(iv, 0, output, salt.length + 4, iv.length);
		// Initialise the cipher and encrypt the plaintext
		try {
			cipher.init(true, key, iv);
			int outputOff = salt.length + 4 + iv.length;
			cipher.process(input, 0, input.length, output, outputOff);
			return output;
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public byte[] decryptWithPassword(byte[] input, String password) {
		AuthenticatedCipher cipher = new AuthenticatedCipherImpl();
		int macBytes = cipher.getMacBytes();
		// The input contains the salt, iterations, IV, ciphertext and MAC
		if (input.length < PBKDF_SALT_BYTES + 4 + STORAGE_IV_BYTES + macBytes)
			return null; // Invalid input
		byte[] salt = new byte[PBKDF_SALT_BYTES];
		System.arraycopy(input, 0, salt, 0, salt.length);
		long iterations = ByteUtils.readUint32(input, salt.length);
		if (iterations < 0 || iterations > Integer.MAX_VALUE)
			return null; // Invalid iteration count
		byte[] iv = new byte[STORAGE_IV_BYTES];
		System.arraycopy(input, salt.length + 4, iv, 0, iv.length);
		// Derive the key from the password
		SecretKey key = new SecretKey(pbkdf2(password, salt, (int) iterations));
		// Initialise the cipher
		try {
			cipher.init(false, key, iv);
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
		// Try to decrypt the ciphertext (may be invalid)
		try {
			int inputOff = salt.length + 4 + iv.length;
			int inputLen = input.length - inputOff;
			byte[] output = new byte[inputLen - macBytes];
			cipher.process(input, inputOff, inputLen, output, 0);
			return output;
		} catch (GeneralSecurityException e) {
			return null; // Invalid ciphertext
		}
	}

	// Key derivation function based on a hash function - see NIST SP 800-56A,
	// section 5.8
	private byte[] concatenationKdf(byte[]... inputs) {
		// The output of the hash function must be long enough to use as a key
		MessageDigest messageDigest = getMessageDigest();
		if (messageDigest.getDigestLength() < SecretKey.LENGTH)
			throw new RuntimeException();
		// Each input is length-prefixed - the length must fit in an
		// unsigned 8-bit integer
		for (byte[] input : inputs) {
			if (input.length > 255) throw new IllegalArgumentException();
			messageDigest.update((byte) input.length);
			messageDigest.update(input);
		}
		byte[] hash = messageDigest.digest();
		// The output is the first SecretKey.LENGTH bytes of the hash
		if (hash.length == SecretKey.LENGTH) return hash;
		byte[] truncated = new byte[SecretKey.LENGTH];
		System.arraycopy(hash, 0, truncated, 0, truncated.length);
		return truncated;
	}

	// Key derivation function based on a PRF in counter mode - see
	// NIST SP 800-108, section 5.1
	private byte[] counterModeKdf(byte[] secret, byte[] label, long context) {
		if (secret.length != SecretKey.LENGTH)
			throw new IllegalArgumentException();
		// The label must be null-terminated
		if (label[label.length - 1] != '\0')
			throw new IllegalArgumentException();
		// Initialise the PRF
		Mac prf = new HMac(new SHA256Digest());
		KeyParameter k = new KeyParameter(secret);
		prf.init(k);
		int macLength = prf.getMacSize();
		// The output of the PRF must be long enough to use as a key
		if (macLength < SecretKey.LENGTH) throw new RuntimeException();
		byte[] mac = new byte[macLength];
		prf.update((byte) 0); // Counter
		prf.update(label, 0, label.length); // Null-terminated
		byte[] contextBytes = new byte[4];
		ByteUtils.writeUint32(context, contextBytes, 0);
		prf.update(contextBytes, 0, contextBytes.length);
		prf.update((byte) SecretKey.LENGTH); // Output length
		prf.doFinal(mac, 0);
		// The output is the first SecretKey.LENGTH bytes of the MAC
		if (mac.length == SecretKey.LENGTH) return mac;
		byte[] truncated = new byte[SecretKey.LENGTH];
		System.arraycopy(mac, 0, truncated, 0, truncated.length);
		return truncated;
	}

	// Password-based key derivation function - see PKCS#5 v2.1, section 5.2
	private byte[] pbkdf2(String password, byte[] salt, int iterations) {
		byte[] utf8 = StringUtils.toUtf8(password);
		Digest digest = new SHA256Digest();
		PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(digest);
		gen.init(utf8, salt, iterations);
		int keyLengthInBits = SecretKey.LENGTH * 8;
		CipherParameters p = gen.generateDerivedParameters(keyLengthInBits);
		return ((KeyParameter) p).getKey();
	}

	// Package access for testing
	int chooseIterationCount(int targetMillis) {
		List<Long> quickSamples = new ArrayList<Long>(PBKDF_SAMPLES);
		List<Long> slowSamples = new ArrayList<Long>(PBKDF_SAMPLES);
		long iterationNanos = 0, initNanos = 0;
		while (iterationNanos <= 0 || initNanos <= 0) {
			// Sample the running time with one iteration and two iterations
			for (int i = 0; i < PBKDF_SAMPLES; i++) {
				quickSamples.add(sampleRunningTime(1));
				slowSamples.add(sampleRunningTime(2));
			}
			// Calculate the iteration time and the initialisation time
			long quickMedian = median(quickSamples);
			long slowMedian = median(slowSamples);
			iterationNanos = slowMedian - quickMedian;
			initNanos = quickMedian - iterationNanos;
			if (LOG.isLoggable(INFO)) {
				LOG.info("Init: " + initNanos + ", iteration: "
						+ iterationNanos);
			}
		}
		long targetNanos = targetMillis * 1000L * 1000L;
		long iterations = (targetNanos - initNanos) / iterationNanos;
		if (LOG.isLoggable(INFO)) LOG.info("Target iterations: " + iterations);
		if (iterations < 1) return 1;
		if (iterations > Integer.MAX_VALUE) return Integer.MAX_VALUE;
		return (int) iterations;
	}

	private long sampleRunningTime(int iterations) {
		byte[] password = { 'p', 'a', 's', 's', 'w', 'o', 'r', 'd' };
		byte[] salt = new byte[PBKDF_SALT_BYTES];
		int keyLengthInBits = SecretKey.LENGTH * 8;
		long start = System.nanoTime();
		Digest digest = new SHA256Digest();
		PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(digest);
		gen.init(password, salt, iterations);
		gen.generateDerivedParameters(keyLengthInBits);
		return System.nanoTime() - start;
	}

	private long median(List<Long> list) {
		int size = list.size();
		if (size == 0) throw new IllegalArgumentException();
		Collections.sort(list);
		if (size % 2 == 1) return list.get(size / 2);
		return list.get(size / 2 - 1) + list.get(size / 2) / 2;
	}
}
