package net.sf.briar.transport;

import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayOutputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import junit.framework.TestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ConnectionEncrypterImplTest extends TestCase {

	private static final int MAC_LENGTH = 32;

	private final Cipher tagCipher, frameCipher;
	private final ErasableKey tagKey, frameKey;

	public ConnectionEncrypterImplTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		tagCipher = crypto.getTagCipher();
		frameCipher = crypto.getFrameCipher();
		tagKey = crypto.generateTestKey();
		frameKey = crypto.generateTestKey();
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
		// Calculate the expected tag
		byte[] tag = TagEncoder.encodeTag(0, tagCipher, tagKey);
		// Calculate the expected ciphertext for the first frame
		byte[] iv = new byte[frameCipher.getBlockSize()];
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
		out.write(tag);
		out.write(ciphertext);
		out.write(ciphertext1);
		byte[] expected = out.toByteArray();
		// Use a ConnectionEncrypter to encrypt the plaintext
		out.reset();
		ConnectionEncrypter e = new ConnectionEncrypterImpl(out, Long.MAX_VALUE,
				tagCipher, frameCipher, tagKey, frameKey);
		e.getOutputStream().write(plaintext);
		e.writeFinal(plaintextMac);
		e.getOutputStream().write(plaintext1);
		e.writeFinal(plaintextMac);
		byte[] actual = out.toByteArray();
		// Check that the actual ciphertext matches the expected ciphertext
		assertArrayEquals(expected, actual);
		assertEquals(Long.MAX_VALUE - actual.length, e.getRemainingCapacity());
	}
}
