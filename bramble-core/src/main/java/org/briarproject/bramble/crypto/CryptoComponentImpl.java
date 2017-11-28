package org.briarproject.bramble.crypto;

import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.crypto.KeyPair;
import org.briarproject.bramble.api.crypto.KeyParser;
import org.briarproject.bramble.api.crypto.PrivateKey;
import org.briarproject.bramble.api.crypto.PublicKey;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.system.SecureRandomProvider;
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
import static org.briarproject.bramble.crypto.EllipticCurveConstants.PARAMETERS;
import static org.briarproject.bramble.util.ByteUtils.INT_32_BYTES;

class CryptoComponentImpl implements CryptoComponent {

	private static final Logger LOG =
			Logger.getLogger(CryptoComponentImpl.class.getName());

	private static final int AGREEMENT_KEY_PAIR_BITS = 256;
	private static final int SIGNATURE_KEY_PAIR_BITS = 256;
	private static final int ED_KEY_PAIR_BITS = 256;
	private static final int STORAGE_IV_BYTES = 24; // 196 bits
	private static final int PBKDF_SALT_BYTES = 32; // 256 bits
	private static final int PBKDF_TARGET_MILLIS = 500;
	private static final int PBKDF_SAMPLES = 30;

	private final SecureRandom secureRandom;
	private final ECKeyPairGenerator agreementKeyPairGenerator;
	private final ECKeyPairGenerator signatureKeyPairGenerator;
	private final KeyParser agreementKeyParser, signatureKeyParser;
	private final MessageEncrypter messageEncrypter;
	private final KeyPairGenerator edKeyPairGenerator;
	private final KeyParser edKeyParser;

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
		edKeyPairGenerator = new KeyPairGenerator();
		edKeyPairGenerator.initialize(ED_KEY_PAIR_BITS, secureRandom);
		edKeyParser = new EdKeyParser();
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
	public KeyPair generateEdKeyPair() {
		java.security.KeyPair keyPair = edKeyPairGenerator.generateKeyPair();
		EdDSAPublicKey edPublicKey = (EdDSAPublicKey) keyPair.getPublic();
		PublicKey publicKey = new EdPublicKey(edPublicKey.getAbyte());
		EdDSAPrivateKey edPrivateKey = (EdDSAPrivateKey) keyPair.getPrivate();
		PrivateKey privateKey = new EdPrivateKey(edPrivateKey.getSeed());
		return new KeyPair(publicKey, privateKey);
	}

	@Override
	public KeyParser getEdKeyParser() {
		return edKeyParser;
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
	public SecretKey deriveKey(String label, SecretKey k, byte[]... inputs) {
		byte[] mac = mac(label, k, inputs);
		if (mac.length != SecretKey.LENGTH) throw new IllegalStateException();
		return new SecretKey(mac);
	}

	@Override
	public SecretKey deriveSharedSecret(String label, PublicKey theirPublicKey,
			KeyPair ourKeyPair, byte[]... inputs)
			throws GeneralSecurityException {
		PrivateKey ourPriv = ourKeyPair.getPrivate();
		byte[][] hashInputs = new byte[inputs.length + 1][];
		hashInputs[0] = performRawKeyAgreement(ourPriv, theirPublicKey);
		System.arraycopy(inputs, 0, hashInputs, 1, inputs.length);
		byte[] hash = hash(label, hashInputs);
		if (hash.length != SecretKey.LENGTH) throw new IllegalStateException();
		return new SecretKey(hash);
	}

	@Override
	public byte[] sign(String label, byte[] toSign, byte[] privateKey)
			throws GeneralSecurityException {
		return sign(new SignatureImpl(secureRandom), signatureKeyParser, label,
				toSign, privateKey);
	}

	@Override
	public byte[] signEd(String label, byte[] toSign, byte[] privateKey)
			throws GeneralSecurityException {
		return sign(new EdSignature(), edKeyParser, label, toSign, privateKey);
	}

	private byte[] sign(Signature sig, KeyParser keyParser, String label,
			byte[] toSign, byte[] privateKey) throws GeneralSecurityException {
		PrivateKey key = keyParser.parsePrivateKey(privateKey);
		sig.initSign(key);
		updateSignature(sig, label, toSign);
		return sig.sign();
	}

	@Override
	public boolean verify(String label, byte[] signedData, byte[] publicKey,
			byte[] signature) throws GeneralSecurityException {
		return verify(new SignatureImpl(secureRandom), signatureKeyParser,
				label, signedData, publicKey, signature);
	}

	@Override
	public boolean verifyEd(String label, byte[] signedData, byte[] publicKey,
			byte[] signature) throws GeneralSecurityException {
		return verify(new EdSignature(), edKeyParser, label, signedData,
				publicKey, signature);
	}

	private boolean verify(Signature sig, KeyParser keyParser, String label,
			byte[] signedData, byte[] publicKey, byte[] signature)
			throws GeneralSecurityException {
		PublicKey key = keyParser.parsePublicKey(publicKey);
		sig.initVerify(key);
		updateSignature(sig, label, signedData);
		return sig.verify(signature);
	}

	private void updateSignature(Signature signature, String label,
			byte[] toSign) throws GeneralSecurityException {
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
	public byte[] mac(String label, SecretKey macKey, byte[]... inputs) {
		byte[] labelBytes = StringUtils.toUtf8(label);
		Digest mac = new Blake2sDigest(macKey.getBytes());
		byte[] length = new byte[INT_32_BYTES];
		ByteUtils.writeUint32(labelBytes.length, length, 0);
		mac.update(length, 0, length.length);
		mac.update(labelBytes, 0, labelBytes.length);
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
		List<Long> quickSamples = new ArrayList<>(PBKDF_SAMPLES);
		List<Long> slowSamples = new ArrayList<>(PBKDF_SAMPLES);
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
