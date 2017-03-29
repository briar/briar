package org.briarproject.bramble.crypto;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.KeyParser;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PseudoRandom;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.system.SecureRandomProvider;
import org.briarproject.bramble.api.transport.IncomingKeys;
import org.briarproject.bramble.api.transport.OutgoingKeys;
import org.briarproject.bramble.api.transport.TransportKeys;
import org.briarproject.bramble.util.ByteUtils;
import org.briarproject.bramble.util.StringUtils;
import org.spongycastle.crypto.AsymmetricCipherKeyPair;
import org.spongycastle.crypto.CipherParameters;
import org.spongycastle.crypto.CryptoException;
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
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static org.briarproject.bramble.api.invitation.InvitationConstants.CODE_BITS;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.COMMIT_LENGTH;
import static org.briarproject.bramble.api.transport.TransportConstants.TAG_LENGTH;
import static org.briarproject.bramble.crypto.EllipticCurveConstants.PARAMETERS;
import static org.briarproject.bramble.util.ByteUtils.INT_32_BYTES;
import static org.briarproject.bramble.util.ByteUtils.INT_64_BYTES;
import static org.briarproject.bramble.util.ByteUtils.MAX_32_BIT_UNSIGNED;

class CryptoComponentImpl implements CryptoComponent {

	private static final Logger LOG =
			Logger.getLogger(CryptoComponentImpl.class.getName());

	private static final int AGREEMENT_KEY_PAIR_BITS = 256;
	private static final int SIGNATURE_KEY_PAIR_BITS = 256;
	private static final int STORAGE_IV_BYTES = 24; // 196 bits
	private static final int PBKDF_SALT_BYTES = 32; // 256 bits
	private static final int PBKDF_TARGET_MILLIS = 500;
	private static final int PBKDF_SAMPLES = 30;
	private static final int HASH_SIZE = 256 / 8;

	private static byte[] ascii(String s) {
		return s.getBytes(Charset.forName("US-ASCII"));
	}

	// KDF labels for bluetooth confirmation code derivation
	private static final byte[] BT_A_CONFIRM = ascii("ALICE_CONFIRMATION_CODE");
	private static final byte[] BT_B_CONFIRM = ascii("BOB_CONFIRMATION_CODE");
	// KDF labels for contact exchange stream header key derivation
	private static final byte[] A_INVITE = ascii("ALICE_INVITATION_KEY");
	private static final byte[] B_INVITE = ascii("BOB_INVITATION_KEY");
	// KDF labels for contact exchange signature nonce derivation
	private static final byte[] A_SIG_NONCE = ascii("ALICE_SIGNATURE_NONCE");
	private static final byte[] B_SIG_NONCE = ascii("BOB_SIGNATURE_NONCE");
	// Hash label for BQP public key commitment derivation
	private static final String COMMIT =
			"org.briarproject.bramble.COMMIT";
	// Hash label for shared secret derivation
	private static final String SHARED_SECRET =
			"org.briarproject.bramble.SHARED_SECRET";
	// KDF label for BQP confirmation key derivation
	private static final byte[] CONFIRMATION_KEY = ascii("CONFIRMATION_KEY");
	// KDF label for master key derivation
	private static final byte[] MASTER_KEY = ascii("MASTER_KEY");
	// KDF labels for tag key derivation
	private static final byte[] A_TAG = ascii("ALICE_TAG_KEY");
	private static final byte[] B_TAG = ascii("BOB_TAG_KEY");
	// KDF labels for header key derivation
	private static final byte[] A_HEADER = ascii("ALICE_HEADER_KEY");
	private static final byte[] B_HEADER = ascii("BOB_HEADER_KEY");
	// KDF labels for MAC key derivation
	private static final byte[] A_MAC = ascii("ALICE_MAC_KEY");
	private static final byte[] B_MAC = ascii("BOB_MAC_KEY");
	// KDF label for key rotation
	private static final byte[] ROTATE = ascii("ROTATE");

	private final SecureRandom secureRandom;
	private final ECKeyPairGenerator agreementKeyPairGenerator;
	private final ECKeyPairGenerator signatureKeyPairGenerator;
	private final KeyParser agreementKeyParser, signatureKeyParser;
	private final MessageEncrypter messageEncrypter;

	@Inject
	CryptoComponentImpl(SecureRandomProvider secureRandomProvider) {
		if (LOG.isLoggable(INFO)) {
			SecureRandom defaultSecureRandom = new SecureRandom();
			String name = defaultSecureRandom.getProvider().getName();
			String algorithm = defaultSecureRandom.getAlgorithm();
			LOG.info("Default SecureRandom: " + name + " " + algorithm);
		}
		Provider provider = secureRandomProvider.getProvider();
		if (provider == null) {
			LOG.info("Using default");
		} else {
			installSecureRandomProvider(provider);
			if (LOG.isLoggable(INFO)) {
				SecureRandom installedSecureRandom = new SecureRandom();
				String name = installedSecureRandom.getProvider().getName();
				String algorithm = installedSecureRandom.getAlgorithm();
				LOG.info("Installed SecureRandom: " + name + " " + algorithm);
			}
		}
		secureRandom = new SecureRandom();
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
		messageEncrypter = new MessageEncrypter(secureRandom);
	}

	// Based on https://android-developers.googleblog.com/2013/08/some-securerandom-thoughts.html
	private void installSecureRandomProvider(Provider provider) {
		Provider[] providers = Security.getProviders("SecureRandom.SHA1PRNG");
		if (providers == null || providers.length == 0
				|| !provider.getClass().equals(providers[0].getClass())) {
			Security.insertProviderAt(provider, 1);
		}
		// Check the new provider is the default when no algorithm is specified
		SecureRandom random = new SecureRandom();
		if (!provider.getClass().equals(random.getProvider().getClass())) {
			throw new SecurityException("Wrong SecureRandom provider: "
					+ random.getProvider().getClass());
		}
		// Check the new provider is the default when SHA1PRNG is specified
		try {
			random = SecureRandom.getInstance("SHA1PRNG");
		} catch (NoSuchAlgorithmException e) {
			throw new SecurityException(e);
		}
		if (!provider.getClass().equals(random.getProvider().getClass())) {
			throw new SecurityException("Wrong SHA1PRNG provider: "
					+ random.getProvider().getClass());
		}
	}

	@Override
	public SecretKey generateSecretKey() {
		byte[] b = new byte[SecretKey.LENGTH];
		secureRandom.nextBytes(b);
		return new SecretKey(b);
	}

	@Override
	public PseudoRandom getPseudoRandom(int seed1, int seed2) {
		byte[] seed = new byte[INT_32_BYTES * 2];
		ByteUtils.writeUint32(seed1, seed, 0);
		ByteUtils.writeUint32(seed2, seed, INT_32_BYTES);
		return new PseudoRandomImpl(seed);
	}

	@Override
	public SecureRandom getSecureRandom() {
		return secureRandom;
	}

	// Package access for testing
	byte[] performRawKeyAgreement(PrivateKey priv, PublicKey pub)
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

	@Override
	public KeyPair generateAgreementKeyPair() {
		AsymmetricCipherKeyPair keyPair =
				agreementKeyPairGenerator.generateKeyPair();
		// Return a wrapper that uses the SEC 1 encoding
		ECPublicKeyParameters ecPublicKey =
				(ECPublicKeyParameters) keyPair.getPublic();
		PublicKey publicKey = new Sec1PublicKey(ecPublicKey
		);
		ECPrivateKeyParameters ecPrivateKey =
				(ECPrivateKeyParameters) keyPair.getPrivate();
		PrivateKey privateKey = new Sec1PrivateKey(ecPrivateKey,
				AGREEMENT_KEY_PAIR_BITS);
		return new KeyPair(publicKey, privateKey);
	}

	@Override
	public KeyParser getAgreementKeyParser() {
		return agreementKeyParser;
	}

	@Override
	public KeyPair generateSignatureKeyPair() {
		AsymmetricCipherKeyPair keyPair =
				signatureKeyPairGenerator.generateKeyPair();
		// Return a wrapper that uses the SEC 1 encoding
		ECPublicKeyParameters ecPublicKey =
				(ECPublicKeyParameters) keyPair.getPublic();
		PublicKey publicKey = new Sec1PublicKey(ecPublicKey
		);
		ECPrivateKeyParameters ecPrivateKey =
				(ECPrivateKeyParameters) keyPair.getPrivate();
		PrivateKey privateKey = new Sec1PrivateKey(ecPrivateKey,
				SIGNATURE_KEY_PAIR_BITS);
		return new KeyPair(publicKey, privateKey);
	}

	@Override
	public KeyParser getSignatureKeyParser() {
		return signatureKeyParser;
	}

	@Override
	public KeyParser getMessageKeyParser() {
		return messageEncrypter.getKeyParser();
	}

	@Override
	public int generateBTInvitationCode() {
		int codeBytes = (CODE_BITS + 7) / 8;
		byte[] random = new byte[codeBytes];
		secureRandom.nextBytes(random);
		return ByteUtils.readUint(random, CODE_BITS);
	}

	@Override
	public int deriveBTConfirmationCode(SecretKey master, boolean alice) {
		byte[] b = macKdf(master, alice ? BT_A_CONFIRM : BT_B_CONFIRM);
		return ByteUtils.readUint(b, CODE_BITS);
	}

	@Override
	public SecretKey deriveHeaderKey(SecretKey master,
			boolean alice) {
		return new SecretKey(macKdf(master, alice ? A_INVITE : B_INVITE));
	}

	@Override
	public SecretKey deriveMacKey(SecretKey master, boolean alice) {
		return new SecretKey(macKdf(master, alice ? A_MAC : B_MAC));
	}

	@Override
	public byte[] deriveSignatureNonce(SecretKey master,
			boolean alice) {
		return macKdf(master, alice ? A_SIG_NONCE : B_SIG_NONCE);
	}

	@Override
	public byte[] deriveKeyCommitment(byte[] publicKey) {
		byte[] hash = hash(COMMIT, publicKey);
		// The output is the first COMMIT_LENGTH bytes of the hash
		byte[] commitment = new byte[COMMIT_LENGTH];
		System.arraycopy(hash, 0, commitment, 0, COMMIT_LENGTH);
		return commitment;
	}

	@Override
	public SecretKey deriveSharedSecret(byte[] theirPublicKey,
			KeyPair ourKeyPair, boolean alice) throws GeneralSecurityException {
		PrivateKey ourPriv = ourKeyPair.getPrivate();
		PublicKey theirPub = agreementKeyParser.parsePublicKey(theirPublicKey);
		byte[] raw = performRawKeyAgreement(ourPriv, theirPub);
		byte[] alicePub, bobPub;
		if (alice) {
			alicePub = ourKeyPair.getPublic().getEncoded();
			bobPub = theirPublicKey;
		} else {
			alicePub = theirPublicKey;
			bobPub = ourKeyPair.getPublic().getEncoded();
		}
		return new SecretKey(hash(SHARED_SECRET, raw, alicePub, bobPub));
	}

	@Override
	public byte[] deriveConfirmationRecord(SecretKey sharedSecret,
			byte[] theirPayload, byte[] ourPayload, byte[] theirPublicKey,
			KeyPair ourKeyPair, boolean alice, boolean aliceRecord) {
		SecretKey ck = new SecretKey(macKdf(sharedSecret, CONFIRMATION_KEY));
		byte[] alicePayload, alicePub, bobPayload, bobPub;
		if (alice) {
			alicePayload = ourPayload;
			alicePub = ourKeyPair.getPublic().getEncoded();
			bobPayload = theirPayload;
			bobPub = theirPublicKey;
		} else {
			alicePayload = theirPayload;
			alicePub = theirPublicKey;
			bobPayload = ourPayload;
			bobPub = ourKeyPair.getPublic().getEncoded();
		}
		if (aliceRecord)
			return macKdf(ck, alicePayload, alicePub, bobPayload, bobPub);
		else
			return macKdf(ck, bobPayload, bobPub, alicePayload, alicePub);
	}

	@Override
	public SecretKey deriveMasterSecret(SecretKey sharedSecret) {
		return new SecretKey(macKdf(sharedSecret, MASTER_KEY));
	}

	@Override
	public SecretKey deriveMasterSecret(byte[] theirPublicKey,
			KeyPair ourKeyPair, boolean alice) throws GeneralSecurityException {
		return deriveMasterSecret(deriveSharedSecret(
				theirPublicKey, ourKeyPair, alice));
	}

	@Override
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

	@Override
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
		byte[] period = new byte[INT_64_BYTES];
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

	@Override
	public void encodeTag(byte[] tag, SecretKey tagKey, long streamNumber) {
		if (tag.length < TAG_LENGTH) throw new IllegalArgumentException();
		if (streamNumber < 0 || streamNumber > MAX_32_BIT_UNSIGNED)
			throw new IllegalArgumentException();
		// Initialise the PRF
		Digest prf = new Blake2sDigest(tagKey.getBytes());
		// The output of the PRF must be long enough to use as a tag
		int macLength = prf.getDigestSize();
		if (macLength < TAG_LENGTH) throw new IllegalStateException();
		// The input is the stream number as a 64-bit integer
		byte[] input = new byte[INT_64_BYTES];
		ByteUtils.writeUint64(streamNumber, input, 0);
		prf.update(input, 0, input.length);
		byte[] mac = new byte[macLength];
		prf.doFinal(mac, 0);
		// The output is the first TAG_LENGTH bytes of the MAC
		System.arraycopy(mac, 0, tag, 0, TAG_LENGTH);
	}

	@Override
	public byte[] sign(String label, byte[] toSign, byte[] privateKey)
			throws GeneralSecurityException {
		Signature signature = new SignatureImpl(secureRandom);
		KeyParser keyParser = getSignatureKeyParser();
		PrivateKey key = keyParser.parsePrivateKey(privateKey);
		signature.initSign(key);
		updateSignature(signature, label, toSign);
		return signature.sign();
	}

	@Override
	public boolean verify(String label, byte[] signedData, byte[] publicKey,
			byte[] signature) throws GeneralSecurityException {
		Signature sig = new SignatureImpl(secureRandom);
		KeyParser keyParser = getSignatureKeyParser();
		PublicKey key = keyParser.parsePublicKey(publicKey);
		sig.initVerify(key);
		updateSignature(sig, label, signedData);
		return sig.verify(signature);
	}

	private void updateSignature(Signature signature, String label,
			byte[] toSign) {
		byte[] labelBytes = StringUtils.toUtf8(label);
		byte[] length = new byte[INT_32_BYTES];
		ByteUtils.writeUint32(labelBytes.length, length, 0);
		signature.update(length);
		signature.update(labelBytes);
		ByteUtils.writeUint32(toSign.length, length, 0);
		signature.update(length);
		signature.update(toSign);
	}

	@Override
	public byte[] hash(String label, byte[]... inputs) {
		byte[] labelBytes = StringUtils.toUtf8(label);
		Digest digest = new Blake2sDigest();
		byte[] length = new byte[INT_32_BYTES];
		ByteUtils.writeUint32(labelBytes.length, length, 0);
		digest.update(length, 0, length.length);
		digest.update(labelBytes, 0, labelBytes.length);
		for (byte[] input : inputs) {
			ByteUtils.writeUint32(input.length, length, 0);
			digest.update(length, 0, length.length);
			digest.update(input, 0, input.length);
		}
		byte[] output = new byte[digest.getDigestSize()];
		digest.doFinal(output, 0);
		return output;
	}

	@Override
	public int getHashLength() {
		return HASH_SIZE;
	}

	@Override
	public byte[] mac(SecretKey macKey, byte[]... inputs) {
		Digest mac = new Blake2sDigest(macKey.getBytes());
		byte[] length = new byte[INT_32_BYTES];
		for (byte[] input : inputs) {
			ByteUtils.writeUint32(input.length, length, 0);
			mac.update(length, 0, length.length);
			mac.update(input, 0, input.length);
		}
		byte[] output = new byte[mac.getDigestSize()];
		mac.doFinal(output, 0);
		return output;
	}

	@Override
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
		int outputLen = salt.length + INT_32_BYTES + iv.length + input.length
				+ macBytes;
		byte[] output = new byte[outputLen];
		System.arraycopy(salt, 0, output, 0, salt.length);
		ByteUtils.writeUint32(iterations, output, salt.length);
		System.arraycopy(iv, 0, output, salt.length + INT_32_BYTES, iv.length);
		// Initialise the cipher and encrypt the plaintext
		try {
			cipher.init(true, key, iv);
			int outputOff = salt.length + INT_32_BYTES + iv.length;
			cipher.process(input, 0, input.length, output, outputOff);
			return output;
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public byte[] decryptWithPassword(byte[] input, String password) {
		AuthenticatedCipher cipher = new XSalsa20Poly1305AuthenticatedCipher();
		int macBytes = cipher.getMacBytes();
		// The input contains the salt, iterations, IV, ciphertext and MAC
		if (input.length < PBKDF_SALT_BYTES + INT_32_BYTES + STORAGE_IV_BYTES
				+ macBytes)
			return null; // Invalid input
		byte[] salt = new byte[PBKDF_SALT_BYTES];
		System.arraycopy(input, 0, salt, 0, salt.length);
		long iterations = ByteUtils.readUint32(input, salt.length);
		if (iterations < 0 || iterations > Integer.MAX_VALUE)
			return null; // Invalid iteration count
		byte[] iv = new byte[STORAGE_IV_BYTES];
		System.arraycopy(input, salt.length + INT_32_BYTES, iv, 0, iv.length);
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
			int inputOff = salt.length + INT_32_BYTES + iv.length;
			int inputLen = input.length - inputOff;
			byte[] output = new byte[inputLen - macBytes];
			cipher.process(input, inputOff, inputLen, output, 0);
			return output;
		} catch (GeneralSecurityException e) {
			return null; // Invalid ciphertext
		}
	}

	@Override
	public byte[] encryptToKey(PublicKey publicKey, byte[] plaintext) {
		try {
			return messageEncrypter.encrypt(publicKey, plaintext);
		} catch (CryptoException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String asciiArmour(byte[] b, int lineLength) {
		return AsciiArmour.wrap(b, lineLength);
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
		byte[] length = new byte[INT_32_BYTES];
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
		byte[] password = {'p', 'a', 's', 's', 'w', 'o', 'r', 'd'};
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
