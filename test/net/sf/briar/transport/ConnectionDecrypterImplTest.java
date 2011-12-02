package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;
import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;

import javax.crypto.Cipher;
import net.sf.briar.api.crypto.ErasableKey;
import javax.crypto.spec.IvParameterSpec;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.crypto.CryptoModule;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ConnectionDecrypterImplTest extends TestCase {

	private static final int MAC_LENGTH = 32;

	private final Cipher tagCipher, frameCipher;
	private final ErasableKey tagKey, frameKey;
	private final TransportIndex transportIndex = new TransportIndex(13);
	private final long connection = 12345L;

	public ConnectionDecrypterImplTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		tagCipher = crypto.getTagCipher();
		frameCipher = crypto.getFrameCipher();
		tagKey = crypto.generateTestKey();
		frameKey = crypto.generateTestKey();
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
		byte[] iv = IvEncoder.encodeIv(transportIndex.getInt(), connection);
		tagCipher.init(Cipher.ENCRYPT_MODE, tagKey);
		byte[] tag  = tagCipher.doFinal(iv);
		assertEquals(TAG_LENGTH, tag.length);
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
		ConnectionDecrypter d = new ConnectionDecrypterImpl(in,
				IvEncoder.encodeIv(transportIndex.getInt(), connection),
				frameCipher, frameKey);
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
		assertArrayEquals(expected, actual);
	}
}
