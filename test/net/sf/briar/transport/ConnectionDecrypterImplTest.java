package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.crypto.CryptoModule;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ConnectionDecrypterImplTest extends TestCase {

	private static final int MAC_LENGTH = 32;

	private final Cipher ivCipher, frameCipher;
	private final SecretKey ivKey, frameKey;
	private final int transportId = 1234;
	private final long connection = 12345L;

	public ConnectionDecrypterImplTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		ivCipher = crypto.getIvCipher();
		frameCipher = crypto.getFrameCipher();
		ivKey = crypto.generateSecretKey();
		frameKey = crypto.generateSecretKey();
	}

	@Test
	public void testInitiatorDecryption() throws Exception {
		testDecryption(true);
	}

	@Test
	public void testResponderDecryption() throws Exception {
		testDecryption(false);
	}

	private void testDecryption(boolean initiator) throws Exception {
		// Calculate the plaintext and ciphertext for the IV
		byte[] iv = IvEncoder.encodeIv(initiator, transportId, connection);
		ivCipher.init(Cipher.ENCRYPT_MODE, ivKey);
		byte[] encryptedIv  = ivCipher.doFinal(iv);
		assertEquals(IV_LENGTH, encryptedIv.length);
		// Calculate the expected plaintext for the first frame
		byte[] ciphertext = new byte[123];
		byte[] ciphertextMac = new byte[MAC_LENGTH];
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.DECRYPT_MODE, frameKey, ivSpec);
		byte[] plaintext = new byte[ciphertext.length + ciphertextMac.length];
		int offset = frameCipher.update(ciphertext, 0, ciphertext.length,
				plaintext);
		frameCipher.doFinal(ciphertextMac, 0, ciphertextMac.length, plaintext,
				offset);
		// Calculate the expected plaintext for the second frame
		byte[] ciphertext1 = new byte[1234];
		IvEncoder.updateIv(iv, 1L);
		ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.DECRYPT_MODE, frameKey, ivSpec);
		byte[] plaintext1 = new byte[ciphertext1.length + ciphertextMac.length];
		offset = frameCipher.update(ciphertext1, 0, ciphertext1.length,
				plaintext1);
		frameCipher.doFinal(ciphertextMac, 0, ciphertextMac.length, plaintext1,
				offset);
		// Concatenate the ciphertexts
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(ciphertext);
		out.write(ciphertextMac);
		out.write(ciphertext1);
		out.write(ciphertextMac);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		// Use a ConnectionDecrypter to decrypt the ciphertext
		ConnectionDecrypter d = new ConnectionDecrypterImpl(in, encryptedIv,
				ivCipher, frameCipher, ivKey, frameKey);
		// First frame
		byte[] decrypted = new byte[ciphertext.length];
		TestUtils.readFully(d.getInputStream(), decrypted);
		byte[] decryptedMac = new byte[MAC_LENGTH];
		d.readMac(decryptedMac);
		// Second frame
		byte[] decrypted1 = new byte[ciphertext1.length];
		TestUtils.readFully(d.getInputStream(), decrypted1);
		byte[] decryptedMac1 = new byte[MAC_LENGTH];
		d.readMac(decryptedMac1);
		// Check that the actual plaintext matches the expected plaintext
		out.reset();
		out.write(plaintext);
		out.write(plaintext1);
		byte[] expected = out.toByteArray();
		out.reset();
		out.write(decrypted);
		out.write(decryptedMac);
		out.write(decrypted1);
		out.write(decryptedMac1);
		byte[] actual = out.toByteArray();
		assertTrue(Arrays.equals(expected, actual));
	}
}
