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
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.SecureRandomProvider;
import org.briarproject.bramble.util.ByteUtils;
import org.briarproject.bramble.util.StringUtils;
import org.spongycastle.crypto.CryptoException;
import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.digests.Blake2bDigest;
import org.whispersystems.curve25519.Curve25519;
import org.whispersystems.curve25519.Curve25519KeyPair;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static org.briarproject.bramble.util.ByteUtils.INT_32_BYTES;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.now;

@NotNullByDefault
class CryptoComponentImpl implements CryptoComponent {

	private static final Logger LOG =
			Logger.getLogger(CryptoComponentImpl.class.getName());

	private static final int SIGNATURE_KEY_PAIR_BITS = 256;
	private static final int STORAGE_IV_BYTES = 24; // 196 bits
	private static final int PBKDF_SALT_BYTES = 32; // 256 bits
	private static final int PBKDF_FORMAT_SCRYPT = 0;

	private final SecureRandom secureRandom;
	private final PasswordBasedKdf passwordBasedKdf;
	private final Curve25519 curve25519;
	private final KeyPairGenerator signatureKeyPairGenerator;
	private final KeyParser agreementKeyParser, signatureKeyParser;
	private final MessageEncrypter messageEncrypter;

	@Inject
	CryptoComponentImpl(SecureRandomProvider secureRandomProvider,
			PasswordBasedKdf passwordBasedKdf) {
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
		this.passwordBasedKdf = passwordBasedKdf;
		curve25519 = Curve25519.getInstance("java");
		signatureKeyPairGenerator = new KeyPairGenerator();
		signatureKeyPairGenerator.initialize(SIGNATURE_KEY_PAIR_BITS,
				secureRandom);
		agreementKeyParser = new Curve25519KeyParser();
		signatureKeyParser = new EdKeyParser();
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
	public SecureRandom getSecureRandom() {
		return secureRandom;
	}

	// Package access for testing
	byte[] performRawKeyAgreement(PrivateKey priv, PublicKey pub)
			throws GeneralSecurityException {
		if (!(priv instanceof Curve25519PrivateKey))
			throw new IllegalArgumentException();
		if (!(pub instanceof Curve25519PublicKey))
			throw new IllegalArgumentException();
		long start = now();
		byte[] secret = curve25519.calculateAgreement(pub.getEncoded(),
				priv.getEncoded());
		// If the shared secret is all zeroes, the public key is invalid
		byte allZero = 0;
		for (byte b : secret) allZero |= b;
		if (allZero == 0) throw new GeneralSecurityException();
		logDuration(LOG, "Deriving shared secret", start);
		return secret;
	}

	@Override
	public KeyPair generateAgreementKeyPair() {
		Curve25519KeyPair keyPair = curve25519.generateKeyPair();
		PublicKey pub = new Curve25519PublicKey(keyPair.getPublicKey());
		PrivateKey priv = new Curve25519PrivateKey(keyPair.getPrivateKey());
		return new KeyPair(pub, priv);
	}

	@Override
	public KeyParser getAgreementKeyParser() {
		return agreementKeyParser;
	}

	@Override
	public KeyPair generateSignatureKeyPair() {
		java.security.KeyPair keyPair =
				signatureKeyPairGenerator.generateKeyPair();
		EdDSAPublicKey edPublicKey = (EdDSAPublicKey) keyPair.getPublic();
		PublicKey publicKey = new EdPublicKey(edPublicKey.getAbyte());
		EdDSAPrivateKey edPrivateKey = (EdDSAPrivateKey) keyPair.getPrivate();
		PrivateKey privateKey = new EdPrivateKey(edPrivateKey.getSeed());
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
		PrivateKey key = signatureKeyParser.parsePrivateKey(privateKey);
		Signature sig = new EdSignature();
		sig.initSign(key);
		updateSignature(sig, label, toSign);
		return sig.sign();
	}

	@Override
	public boolean verifySignature(byte[] signature, String label,
			byte[] signed, byte[] publicKey) throws GeneralSecurityException {
		PublicKey key = signatureKeyParser.parsePublicKey(publicKey);
		Signature sig = new EdSignature();
		sig.initVerify(key);
		updateSignature(sig, label, signed);
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
		Digest digest = new Blake2bDigest(256);
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
		Digest mac = new Blake2bDigest(macKey.getBytes(), 32, null, null);
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
	public boolean verifyMac(byte[] mac, String label, SecretKey macKey,
			byte[]... inputs) {
		byte[] expected = mac(label, macKey, inputs);
		if (mac.length != expected.length) return false;
		// Constant-time comparison
		int cmp = 0;
		for (int i = 0; i < mac.length; i++) cmp |= mac[i] ^ expected[i];
		return cmp == 0;
	}

	@Override
	public byte[] encryptWithPassword(byte[] input, String password) {
		AuthenticatedCipher cipher = new XSalsa20Poly1305AuthenticatedCipher();
		int macBytes = cipher.getMacBytes();
		// Generate a random salt
		byte[] salt = new byte[PBKDF_SALT_BYTES];
		secureRandom.nextBytes(salt);
		// Calibrate the KDF
		int cost = passwordBasedKdf.chooseCostParameter();
		// Derive the key from the password
		SecretKey key = passwordBasedKdf.deriveKey(password, salt, cost);
		// Generate a random IV
		byte[] iv = new byte[STORAGE_IV_BYTES];
		secureRandom.nextBytes(iv);
		// The output contains the format version, salt, cost parameter, IV,
		// ciphertext and MAC
		int outputLen = 1 + salt.length + INT_32_BYTES + iv.length
				+ input.length + macBytes;
		byte[] output = new byte[outputLen];
		int outputOff = 0;
		// Format version
		output[outputOff] = PBKDF_FORMAT_SCRYPT;
		outputOff++;
		// Salt
		System.arraycopy(salt, 0, output, outputOff, salt.length);
		outputOff += salt.length;
		// Cost parameter
		ByteUtils.writeUint32(cost, output, outputOff);
		outputOff += INT_32_BYTES;
		// IV
		System.arraycopy(iv, 0, output, outputOff, iv.length);
		outputOff += iv.length;
		// Initialise the cipher and encrypt the plaintext
		try {
			cipher.init(true, key, iv);
			cipher.process(input, 0, input.length, output, outputOff);
			return output;
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	@Nullable
	public byte[] decryptWithPassword(byte[] input, String password) {
		AuthenticatedCipher cipher = new XSalsa20Poly1305AuthenticatedCipher();
		int macBytes = cipher.getMacBytes();
		// The input contains the format version, salt, cost parameter, IV,
		// ciphertext and MAC
		if (input.length < 1 + PBKDF_SALT_BYTES + INT_32_BYTES
				+ STORAGE_IV_BYTES + macBytes)
			return null; // Invalid input
		int inputOff = 0;
		// Format version
		byte formatVersion = input[inputOff];
		inputOff++;
		if (formatVersion != PBKDF_FORMAT_SCRYPT)
			return null; // Unknown format
		// Salt
		byte[] salt = new byte[PBKDF_SALT_BYTES];
		System.arraycopy(input, inputOff, salt, 0, salt.length);
		inputOff += salt.length;
		// Cost parameter
		long cost = ByteUtils.readUint32(input, inputOff);
		inputOff += INT_32_BYTES;
		if (cost < 2 || cost > Integer.MAX_VALUE)
			return null; // Invalid cost parameter
		// IV
		byte[] iv = new byte[STORAGE_IV_BYTES];
		System.arraycopy(input, inputOff, iv, 0, iv.length);
		inputOff += iv.length;
		// Derive the key from the password
		SecretKey key = passwordBasedKdf.deriveKey(password, salt, (int) cost);
		// Initialise the cipher
		try {
			cipher.init(false, key, iv);
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
		// Try to decrypt the ciphertext (may be invalid)
		try {
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
}
