package org.briarproject.crypto;

import org.briarproject.BriarTestCase;
import org.junit.Test;
import org.spongycastle.crypto.AsymmetricCipherKeyPair;
import org.spongycastle.crypto.CryptoException;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.crypto.params.ECPublicKeyParameters;

import java.security.SecureRandom;

import static org.junit.Assert.assertArrayEquals;

public class MessageEncrypterTest extends BriarTestCase {

	private final SecureRandom random = new SecureRandom();

	@Test
	public void testEncryptionAndDecryption() throws Exception {
		MessageEncrypter m = new MessageEncrypter(random);
		AsymmetricCipherKeyPair kp = m.generateKeyPair();
		ECPublicKeyParameters pub = (ECPublicKeyParameters) kp.getPublic();
		ECPrivateKeyParameters priv = (ECPrivateKeyParameters) kp.getPrivate();
		byte[] plaintext = new byte[123];
		random.nextBytes(plaintext);
		byte[] ciphertext = m.encrypt(pub, plaintext);
		byte[] decrypted = m.decrypt(priv, ciphertext);
		assertArrayEquals(plaintext, decrypted);
	}

	@Test(expected = CryptoException.class)
	public void testDecryptionFailsWithAlteredCiphertext() throws Exception {
		MessageEncrypter m = new MessageEncrypter(random);
		AsymmetricCipherKeyPair kp = m.generateKeyPair();
		ECPublicKeyParameters pub = (ECPublicKeyParameters) kp.getPublic();
		ECPrivateKeyParameters priv = (ECPrivateKeyParameters) kp.getPrivate();
		byte[] ciphertext = m.encrypt(pub, new byte[123]);
		ciphertext[random.nextInt(ciphertext.length)] ^= 0xFF;
		m.decrypt(priv, ciphertext);
	}
}
