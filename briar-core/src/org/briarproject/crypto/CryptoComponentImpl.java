package org.briarproject.crypto;

import org.briarproject.api.TransportId;
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
import org.briarproject.api.transport.IncomingKeys;
import org.briarproject.api.transport.OutgoingKeys;
import org.briarproject.api.transport.TransportKeys;
import org.briarproject.util.ByteUtils;
import org.briarproject.util.StringUtils;
import org.spongycastle.crypto.AsymmetricCipherKeyPair;
import org.spongycastle.crypto.CipherParameters;
import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.agreement.ECDHCBasicAgreement;
import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.crypto.generators.ECKeyPairGenerator;
import org.spongycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.spongycastle.crypto.params.ECKeyGenerationParameters;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.crypto.params.ECPublicKeyParameters;
import org.spongycastle.crypto.params.KeyParameter;

import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static org.briarproject.api.invitation.InvitationConstants.CODE_BITS;
import static org.briarproject.api.transport.TransportConstants.TAG_LENGTH;
import static org.briarproject.crypto.EllipticCurveConstants.PARAMETERS;
import static org.briarproject.util.ByteUtils.MAX_32_BIT_UNSIGNED;

class CryptoComponentImpl implements CryptoComponent {

	private static final Logger LOG =
			Logger.getLogger(CryptoComponentImpl.class.getName());

	private static final int AGREEMENT_KEY_PAIR_BITS = 256;
	private static final int SIGNATURE_KEY_PAIR_BITS = 256;
	private static final int STORAGE_IV_BYTES = 24; // 196 bits
	private static final int PBKDF_SALT_BYTES = 32; // 256 bits
	private static final int PBKDF_TARGET_MILLIS = 500;
	private static final int PBKDF_SAMPLES = 30;

	private static byte[] ascii(String s) {
		return s.getBytes(Charset.forName("US-ASCII"));
	}

	// KDF label for master key derivation
	private static final byte[] MASTER = ascii("MASTER");
	// KDF labels for confirmation code derivation
	private static final byte[] A_CONFIRM = ascii("ALICE_CONFIRMATION_CODE");
	private static final byte[] B_CONFIRM = ascii("BOB_CONFIRMATION_CODE");
	// KDF labels for invitation stream header key derivation
	private static final byte[] A_INVITE = ascii("ALICE_INVITATION_KEY");
	private static final byte[] B_INVITE = ascii("BOB_INVITATION_KEY");
	// KDF labels for signature nonce derivation
	private static final byte[] A_NONCE = ascii("ALICE_SIGNATURE_NONCE");
	private static final byte[] B_NONCE = ascii("BOB_SIGNATURE_NONCE");
	// KDF label for group salt derivation
	private static final byte[] SALT = ascii("SALT");
	// KDF labels for tag key derivation
	private static final byte[] A_TAG = ascii("ALICE_TAG_KEY");
	private static final byte[] B_TAG = ascii("BOB_TAG_KEY");
	// KDF labels for header key derivation
	private static final byte[] A_HEADER = ascii("ALICE_HEADER_KEY");
	private static final byte[] B_HEADER = ascii("BOB_HEADER_KEY");
	// KDF label for key rotation
	private static final byte[] ROTATE = ascii("ROTATE");

	private final SecureRandom secureRandom;
	private final ECKeyPairGenerator agreementKeyPairGenerator;
	private final ECKeyPairGenerator signatureKeyPairGenerator;
	private final KeyParser agreementKeyParser, signatureKeyParser;

	@Inject
	CryptoComponentImpl(SeedProvider seedProvider) {
		if (!FortunaSecureRandom.selfTest()) throw new RuntimeException();
		SecureRandom platformSecureRandom = new SecureRandom();
		if (LOG.isLoggable(INFO)) {
			String provider = platformSecureRandom.getProvider().getName();
			String algorithm = platformSecureRandom.getAlgorithm();
			LOG.info("Default SecureRandom: " + provider + " " + algorithm);
		}
		SecureRandom fortuna = new FortunaSecureRandom(seedProvider.getSeed());
		secureRandom = new CombinedSecureRandom(platformSecureRandom, fortuna);
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
		return new DigestWrapper(new Blake2sDigest());
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

	public SecretKey deriveMasterSecret(byte[] theirPublicKey,
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
		// Derive the master secret from the raw secret using the hash KDF
		return new SecretKey(hashKdf(raw, MASTER, aliceInfo, bobInfo));
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

	public int deriveConfirmationCode(SecretKey master, boolean alice) {
		byte[] b = macKdf(master, alice ? A_CONFIRM : B_CONFIRM);
		return ByteUtils.readUint(b, CODE_BITS);
	}

	public SecretKey deriveInvitationKey(SecretKey master, boolean alice) {
		return new SecretKey(macKdf(master, alice ? A_INVITE : B_INVITE));
	}

	public byte[] deriveSignatureNonce(SecretKey master, boolean alice) {
		return macKdf(master, alice ? A_NONCE : B_NONCE);
	}

	public byte[] deriveGroupSalt(SecretKey master) {
		return macKdf(master, SALT);
	}

	public TransportKeys deriveTransportKeys(TransportId t,
			SecretKey master, long rotationPeriod, boolean alice) {
		// Keys for the previous period are derived from the master secret
		SecretKey inTagPrev = deriveTagKey(master, t, !alice);
		SecretKey inHeaderPrev = deriveHeaderKey(master, t, !alice);
		SecretKey outTagPrev = deriveTagKey(master, t, alice);
		SecretKey outHeaderPrev = deriveHeaderKey(master, t, alice);
		// Derive the keys for the current and next periods
		SecretKey inTagCurr = rotateKey(inTagPrev, rotationPeriod);
		SecretKey inHeaderCurr = rotateKey(inHeaderPrev, rotationPeriod);
		SecretKey inTagNext = rotateKey(inTagCurr, rotationPeriod + 1);
		SecretKey inHeaderNext = rotateKey(inHeaderCurr, rotationPeriod + 1);
		SecretKey outTagCurr = rotateKey(outTagPrev, rotationPeriod);
		SecretKey outHeaderCurr = rotateKey(outHeaderPrev, rotationPeriod);
		// Initialise the reordering windows and stream counters
		IncomingKeys inPrev = new IncomingKeys(inTagPrev, inHeaderPrev,
				rotationPeriod - 1);
		IncomingKeys inCurr = new IncomingKeys(inTagCurr, inHeaderCurr,
				rotationPeriod);
		IncomingKeys inNext = new IncomingKeys(inTagNext, inHeaderNext,
				rotationPeriod + 1);
		OutgoingKeys outCurr = new OutgoingKeys(outTagCurr, outHeaderCurr,
				rotationPeriod);
		// Collect and return the keys
		return new TransportKeys(t, inPrev, inCurr, inNext, outCurr);
	}

	public TransportKeys rotateTransportKeys(TransportKeys k,
			long rotationPeriod) {
		if (k.getRotationPeriod() >= rotationPeriod) return k;
		IncomingKeys inPrev = k.getPreviousIncomingKeys();
		IncomingKeys inCurr = k.getCurrentIncomingKeys();
		IncomingKeys inNext = k.getNextIncomingKeys();
		OutgoingKeys outCurr = k.getCurrentOutgoingKeys();
		long startPeriod = outCurr.getRotationPeriod();
		// Rotate the keys
		for (long p = startPeriod + 1; p <= rotationPeriod; p++) {
			inPrev = inCurr;
			inCurr = inNext;
			SecretKey inNextTag = rotateKey(inNext.getTagKey(), p + 1);
			SecretKey inNextHeader = rotateKey(inNext.getHeaderKey(), p + 1);
			inNext = new IncomingKeys(inNextTag, inNextHeader, p + 1);
			SecretKey outCurrTag = rotateKey(outCurr.getTagKey(), p);
			SecretKey outCurrHeader = rotateKey(outCurr.getHeaderKey(), p);
			outCurr = new OutgoingKeys(outCurrTag, outCurrHeader, p);
		}
		// Collect and return the keys
		return new TransportKeys(k.getTransportId(), inPrev, inCurr, inNext,
				outCurr);
	}

	private SecretKey rotateKey(SecretKey k, long rotationPeriod) {
		byte[] period = new byte[8];
		ByteUtils.writeUint64(rotationPeriod, period, 0);
		return new SecretKey(macKdf(k, ROTATE, period));
	}

	private SecretKey deriveTagKey(SecretKey master, TransportId t,
			boolean alice) {
		byte[] id = StringUtils.toUtf8(t.getString());
		return new SecretKey(macKdf(master, alice ? A_TAG : B_TAG, id));
	}

	private SecretKey deriveHeaderKey(SecretKey master, TransportId t,
			boolean alice) {
		byte[] id = StringUtils.toUtf8(t.getString());
		return new SecretKey(macKdf(master, alice ? A_HEADER : B_HEADER, id));
	}

	public void encodeTag(byte[] tag, SecretKey tagKey, long streamNumber) {
		if (tag.length < TAG_LENGTH) throw new IllegalArgumentException();
		if (streamNumber < 0 || streamNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		// Initialise the PRF
		Digest prf = new Blake2sDigest(tagKey.getBytes());
		// The output of the PRF must be long enough to use as a key
		int macLength = prf.getDigestSize();
		if (macLength < TAG_LENGTH) throw new IllegalStateException();
		// The input is the stream number as a 64-bit integer
		byte[] input = new byte[8];
		ByteUtils.writeUint64(streamNumber, input, 0);
		prf.update(input, 0, input.length);
		byte[] mac = new byte[macLength];
		prf.doFinal(mac, 0);
		// The output is the first TAG_LENGTH bytes of the MAC
		System.arraycopy(mac, 0, tag, 0, TAG_LENGTH);
	}

	public byte[] encryptWithPassword(byte[] input, String password) {
		AuthenticatedCipher cipher = new XSalsa20Poly1305AuthenticatedCipher();
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
		AuthenticatedCipher cipher = new XSalsa20Poly1305AuthenticatedCipher();
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
	private byte[] hashKdf(byte[]... inputs) {
		Digest digest = new Blake2sDigest();
		// The output of the hash function must be long enough to use as a key
		int hashLength = digest.getDigestSize();
		if (hashLength < SecretKey.LENGTH) throw new IllegalStateException();
		// Calculate the hash over the concatenated length-prefixed inputs
		byte[] length = new byte[4];
		for (byte[] input : inputs) {
			ByteUtils.writeUint32(input.length, length, 0);
			digest.update(length, 0, length.length);
			digest.update(input, 0, input.length);
		}
		byte[] hash = new byte[hashLength];
		digest.doFinal(hash, 0);
		// The output is the first SecretKey.LENGTH bytes of the hash
		if (hash.length == SecretKey.LENGTH) return hash;
		byte[] truncated = new byte[SecretKey.LENGTH];
		System.arraycopy(hash, 0, truncated, 0, truncated.length);
		return truncated;
	}

	// Key derivation function based on a pseudo-random function - see
	// NIST SP 800-108, section 5.1
	private byte[] macKdf(SecretKey key, byte[]... inputs) {
		// Initialise the PRF
		Digest prf = new Blake2sDigest(key.getBytes());
		// The output of the PRF must be long enough to use as a key
		int macLength = prf.getDigestSize();
		if (macLength < SecretKey.LENGTH) throw new IllegalStateException();
		// Calculate the PRF over the concatenated length-prefixed inputs
		byte[] length = new byte[4];
		for (byte[] input : inputs) {
			ByteUtils.writeUint32(input.length, length, 0);
			prf.update(length, 0, length.length);
			prf.update(input, 0, input.length);
		}
		byte[] mac = new byte[macLength];
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
