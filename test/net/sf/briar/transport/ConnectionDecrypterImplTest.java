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

	private final Cipher frameCipher;
	private final SecretKey frameKey;
	private final int transportId = 1234;
	private final long connection = 12345L;

	public ConnectionDecrypterImplTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		frameCipher = crypto.getFrameCipher();
		frameKey = crypto.generateSecretKey();
	}

	@Test
	public void testSingleByteFrame() throws Exception {
		// Create a fake ciphertext frame: one byte plus a MAC
		byte[] ciphertext = new byte[1 + MAC_LENGTH];
		ByteArrayInputStream in = new ByteArrayInputStream(ciphertext);
		// Check that one byte plus a MAC can be read
		ConnectionDecrypter d = new ConnectionDecrypterImpl(in, transportId,
				connection, frameCipher, frameKey);
		assertFalse(d.getInputStream().read() == -1);
		d.readMac(new byte[MAC_LENGTH]);
		assertTrue(d.getInputStream().read() == -1);
	}

	@Test
	public void testDecryption() throws Exception {
		// Calculate the expected plaintext for the first frame
		byte[] ciphertext = new byte[123];
		byte[] iv = new byte[IV_LENGTH];
		IvEncoder.encodeIv(iv, transportId, connection, 0L);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.DECRYPT_MODE, frameKey, ivSpec);
		byte[] plaintext = frameCipher.doFinal(ciphertext);
		// Calculate the expected plaintext for the second frame
		byte[] ciphertext1 = new byte[1234];
		IvEncoder.encodeIv(iv, transportId, connection, 1L);
		ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.DECRYPT_MODE, frameKey, ivSpec);
		byte[] plaintext1 = frameCipher.doFinal(ciphertext1);
		assertEquals(ciphertext1.length, plaintext1.length);
		// Concatenate the ciphertexts
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(ciphertext);
		out.write(ciphertext1);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		// Use a ConnectionDecrypter to decrypt the ciphertext
		ConnectionDecrypter d = new ConnectionDecrypterImpl(in, transportId,
				connection, frameCipher, frameKey);
		// First frame
		byte[] decrypted = new byte[plaintext.length - MAC_LENGTH];
		TestUtils.readFully(d.getInputStream(), decrypted);
		byte[] decryptedMac = new byte[MAC_LENGTH];
		d.readMac(decryptedMac);
		// Second frame
		byte[] decrypted1 = new byte[plaintext1.length - MAC_LENGTH];
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
