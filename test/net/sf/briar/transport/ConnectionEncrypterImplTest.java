package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import junit.framework.TestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ConnectionEncrypterImplTest extends TestCase {

	private static final int MAC_LENGTH = 32;

	private final Cipher ivCipher, frameCipher;
	private final SecretKey ivKey, frameKey;
	private final int transportId = 1234;
	private final long connection = 12345L;

	public ConnectionEncrypterImplTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		ivCipher = crypto.getIvCipher();
		frameCipher = crypto.getFrameCipher();
		ivKey = crypto.generateSecretKey();
		frameKey = crypto.generateSecretKey();
	}

	@Test
	public void testSingleByteFrame() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionEncrypter e = new ConnectionEncrypterImpl(out, true,
				transportId, connection, ivCipher, frameCipher, ivKey,
				frameKey);
		e.getOutputStream().write((byte) 0);
		e.writeMac(new byte[MAC_LENGTH]);
		assertEquals(IV_LENGTH + 1 + MAC_LENGTH, out.toByteArray().length);
	}

	@Test
	public void testInitiatorEncryption() throws Exception {
		testEncryption(true);
	}

	@Test
	public void testResponderEncryption() throws Exception {
		testEncryption(false);
	}

	private void testEncryption(boolean initiator) throws Exception {
		// Calculate the expected ciphertext for the IV
		byte[] iv = IvEncoder.encodeIv(initiator, transportId, connection);
		assertEquals(IV_LENGTH, iv.length);
		ivCipher.init(Cipher.ENCRYPT_MODE, ivKey);
		byte[] encryptedIv = ivCipher.doFinal(iv);
		assertEquals(IV_LENGTH, encryptedIv.length);
		// Calculate the expected ciphertext for the first frame
		byte[] plaintext = new byte[123];
		byte[] plaintextMac = new byte[MAC_LENGTH];
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		byte[] ciphertext = new byte[plaintext.length + plaintextMac.length];
		int offset = frameCipher.update(plaintext, 0, plaintext.length,
				ciphertext);
		frameCipher.doFinal(plaintextMac, 0, plaintextMac.length, ciphertext,
				offset);
		// Calculate the expected ciphertext for the second frame
		byte[] plaintext1 = new byte[1234];
		IvEncoder.updateIv(iv, 1L);
		ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		byte[] ciphertext1 = new byte[plaintext1.length + plaintextMac.length];
		offset = frameCipher.update(plaintext1, 0, plaintext1.length,
				ciphertext1);
		frameCipher.doFinal(plaintextMac, 0, plaintextMac.length, ciphertext1,
				offset);
		// Concatenate the ciphertexts
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(encryptedIv);
		out.write(ciphertext);
		out.write(ciphertext1);
		byte[] expected = out.toByteArray();
		// Use a ConnectionEncrypter to encrypt the plaintext
		out.reset();
		ConnectionEncrypter e = new ConnectionEncrypterImpl(out, initiator,
				transportId, connection, ivCipher, frameCipher, ivKey,
				frameKey);
		e.getOutputStream().write(plaintext);
		e.writeMac(plaintextMac);
		e.getOutputStream().write(plaintext1);
		e.writeMac(plaintextMac);
		byte[] actual = out.toByteArray();
		// Check that the actual ciphertext matches the expected ciphertext
		assertTrue(Arrays.equals(expected, actual));
	}
}
