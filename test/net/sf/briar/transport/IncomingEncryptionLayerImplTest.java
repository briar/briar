package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.ByteArrayInputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.crypto.IvEncoder;
import net.sf.briar.crypto.CryptoModule;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class IncomingEncryptionLayerImplTest extends BriarTestCase {

	private final Cipher tagCipher, frameCipher, framePeekingCipher;
	private final IvEncoder frameIvEncoder, framePeekingIvEncoder;
	private final ErasableKey tagKey, frameKey;

	public IncomingEncryptionLayerImplTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		tagCipher = crypto.getTagCipher();
		frameCipher = crypto.getFrameCipher();
		framePeekingCipher = crypto.getFramePeekingCipher();
		frameIvEncoder = crypto.getFrameIvEncoder();
		framePeekingIvEncoder = crypto.getFramePeekingIvEncoder();
		tagKey = crypto.generateTestKey();
		frameKey = crypto.generateTestKey();
	}

	@Test
	public void testDecryptionWithTag() throws Exception {
		// Calculate the tag
		byte[] tag = new byte[TAG_LENGTH];
		TagEncoder.encodeTag(tag, tagCipher, tagKey);
		// Calculate the ciphertext for the first frame
		byte[] plaintext = new byte[FRAME_HEADER_LENGTH + 123];
		HeaderEncoder.encodeHeader(plaintext, 0L, 123, 0, false);
		byte[] iv = frameIvEncoder.encodeIv(0L);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		byte[] ciphertext = frameCipher.doFinal(plaintext);
		// Calculate the ciphertext for the second frame
		byte[] plaintext1 = new byte[FRAME_HEADER_LENGTH + 1234];
		HeaderEncoder.encodeHeader(plaintext1, 1L, 1234, 0, false);
		frameIvEncoder.updateIv(iv, 1L);
		ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		byte[] ciphertext1 = frameCipher.doFinal(plaintext1, 0,
				plaintext1.length);
		// Concatenate the ciphertexts, including the tag
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(tag);
		out.write(ciphertext);
		out.write(ciphertext1);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		// Use the encryption layer to decrypt the ciphertext
		FrameReader decrypter = new IncomingEncryptionLayerImpl(in, tagCipher,
				frameCipher, framePeekingCipher, frameIvEncoder,
				framePeekingIvEncoder, tagKey, frameKey, true);
		// First frame
		Frame f = new Frame();
		assertTrue(decrypter.readFrame(f));
		assertEquals(plaintext.length, f.getLength());
		byte[] decrypted = f.getBuffer();
		assertEquals(0L, HeaderEncoder.getFrameNumber(decrypted));
		for(int i = 0; i < plaintext.length; i++) {
			assertEquals(plaintext[i], decrypted[i]);
		}
		// Second frame
		assertTrue(decrypter.readFrame(f));
		assertEquals(plaintext1.length, f.getLength());
		decrypted = f.getBuffer();
		assertEquals(1L, HeaderEncoder.getFrameNumber(decrypted));
		for(int i = 0; i < plaintext1.length; i++) {
			assertEquals(plaintext1[i], decrypted[i]);
		}
	}

	@Test
	public void testDecryptionWithoutTag() throws Exception {
		// Calculate the ciphertext for the first frame
		byte[] plaintext = new byte[FRAME_HEADER_LENGTH + 123];
		HeaderEncoder.encodeHeader(plaintext, 0L, 123, 0, false);
		byte[] iv = frameIvEncoder.encodeIv(0L);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		byte[] ciphertext = frameCipher.doFinal(plaintext);
		// Calculate the ciphertext for the second frame
		byte[] plaintext1 = new byte[FRAME_HEADER_LENGTH + 1234];
		HeaderEncoder.encodeHeader(plaintext1, 1L, 1234, 0, false);
		frameIvEncoder.updateIv(iv, 1L);
		ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		byte[] ciphertext1 = frameCipher.doFinal(plaintext1, 0,
				plaintext1.length);
		// Concatenate the ciphertexts, excluding the tag
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(ciphertext);
		out.write(ciphertext1);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		// Use the encryption layer to decrypt the ciphertext
		FrameReader decrypter = new IncomingEncryptionLayerImpl(in, tagCipher,
				frameCipher, framePeekingCipher, frameIvEncoder,
				framePeekingIvEncoder, tagKey, frameKey, false);
		// First frame
		Frame f = new Frame();
		assertTrue(decrypter.readFrame(f));
		assertEquals(plaintext.length, f.getLength());
		byte[] decrypted = f.getBuffer();
		assertEquals(0L, HeaderEncoder.getFrameNumber(decrypted));
		for(int i = 0; i < plaintext.length; i++) {
			assertEquals(plaintext[i], decrypted[i]);
		}
		// Second frame
		assertTrue(decrypter.readFrame(f));
		assertEquals(plaintext1.length, f.getLength());
		assertEquals(1L, HeaderEncoder.getFrameNumber(decrypted));
		decrypted = f.getBuffer();
		for(int i = 0; i < plaintext1.length; i++) {
			assertEquals(plaintext1[i], decrypted[i]);
		}
	}
}
