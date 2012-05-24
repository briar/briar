package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;
import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayOutputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.crypto.IvEncoder;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class OutgoingEncryptionLayerTest extends BriarTestCase {

	private final Cipher tagCipher, frameCipher;
	private final IvEncoder frameIvEncoder;
	private final ErasableKey tagKey, frameKey;

	public OutgoingEncryptionLayerTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		tagCipher = crypto.getTagCipher();
		frameCipher = crypto.getFrameCipher();
		frameIvEncoder = crypto.getFrameIvEncoder();
		tagKey = crypto.generateTestKey();
		frameKey = crypto.generateTestKey();
	}

	@Test
	public void testEncryptionWithTag() throws Exception {
		// Calculate the expected tag
		byte[] tag = new byte[TAG_LENGTH];
		TagEncoder.encodeTag(tag, tagCipher, tagKey);
		// Calculate the expected ciphertext for the first frame
		byte[] iv = frameIvEncoder.encodeIv(0L);
		byte[] plaintext = new byte[HEADER_LENGTH + 123];
		HeaderEncoder.encodeHeader(plaintext, 0L, 123, 0, false);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		byte[] ciphertext = frameCipher.doFinal(plaintext);
		// Calculate the expected ciphertext for the second frame
		byte[] plaintext1 = new byte[HEADER_LENGTH + 1234];
		HeaderEncoder.encodeHeader(plaintext1, 1L, 1234, 0, true);
		frameIvEncoder.updateIv(iv, 1L);
		ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		byte[] ciphertext1 = frameCipher.doFinal(plaintext1);
		// Concatenate the ciphertexts
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(tag);
		out.write(ciphertext);
		out.write(ciphertext1);
		byte[] expected = out.toByteArray();
		// Use the encryption layer to encrypt the plaintext
		out.reset();
		FrameWriter encrypter = new OutgoingEncryptionLayer(out, Long.MAX_VALUE,
				tagCipher, frameCipher, frameIvEncoder, tagKey, frameKey);
		encrypter.writeFrame(plaintext);
		encrypter.writeFrame(plaintext1);
		byte[] actual = out.toByteArray();
		// Check that the actual ciphertext matches the expected ciphertext
		assertArrayEquals(expected, actual);
		assertEquals(Long.MAX_VALUE - actual.length,
				encrypter.getRemainingCapacity());
	}
}
