package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.IOException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.plugins.Segment;
import net.sf.briar.api.plugins.SegmentSource;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class IncomingSegmentedEncryptionLayerTest extends BriarTestCase {

	private final Cipher tagCipher, frameCipher;
	private final ErasableKey tagKey, frameKey;

	public IncomingSegmentedEncryptionLayerTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		tagCipher = crypto.getTagCipher();
		frameCipher = crypto.getFrameCipher();
		tagKey = crypto.generateTestKey();
		frameKey = crypto.generateTestKey();
	}

	@Test
	public void testDecryptionWithFirstSegmentTagged() throws Exception {
		// Calculate the ciphertext for the first segment
		byte[] plaintext = new byte[FRAME_HEADER_LENGTH + 123 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(plaintext, 0L, 123, 0);
		byte[] iv = IvEncoder.encodeIv(0L, frameCipher.getBlockSize());
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		byte[] ciphertext = frameCipher.doFinal(plaintext, 0, plaintext.length);
		// Calculate the ciphertext for the second segment
		byte[] plaintext1 = new byte[FRAME_HEADER_LENGTH + 1234 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(plaintext1, 1L, 1234, 0);
		IvEncoder.updateIv(iv, 1L);
		ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		byte[] ciphertext1 = frameCipher.doFinal(plaintext1, 0,
				plaintext1.length);
		// Use the encryption layer to decrypt the ciphertext
		byte[][] frames = new byte[][] { ciphertext, ciphertext1 };
		SegmentSource in = new ByteArraySegmentSource(frames);
		IncomingEncryptionLayer decrypter =
			new IncomingSegmentedEncryptionLayer(in, tagCipher, frameCipher,
					tagKey, frameKey, false);
		// First frame
		Segment s = new SegmentImpl();
		assertTrue(decrypter.readSegment(s));
		assertEquals(plaintext.length, s.getLength());
		assertEquals(0L, s.getSegmentNumber());
		byte[] decrypted = s.getBuffer();
		for(int i = 0; i < plaintext.length; i++) {
			assertEquals(plaintext[i], decrypted[i]);
		}
		// Second frame
		assertTrue(decrypter.readSegment(s));
		assertEquals(plaintext1.length, s.getLength());
		assertEquals(1L, s.getSegmentNumber());
		decrypted = s.getBuffer();
		for(int i = 0; i < plaintext1.length; i++) {
			assertEquals(plaintext1[i], decrypted[i]);
		}
	}

	@Test
	public void testDecryptionWithEverySegmentTagged() throws Exception {
		// Calculate the ciphertext for the first frame
		byte[] plaintext = new byte[FRAME_HEADER_LENGTH + 123 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(plaintext, 0L, 123, 0);
		byte[] iv = IvEncoder.encodeIv(0L, frameCipher.getBlockSize());
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		byte[] ciphertext = frameCipher.doFinal(plaintext, 0, plaintext.length);
		// Calculate the ciphertext for the second frame, including its tag
		byte[] plaintext1 = new byte[FRAME_HEADER_LENGTH + 1234 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(plaintext1, 1L, 1234, 0);
		byte[] ciphertext1 = new byte[TAG_LENGTH + plaintext1.length];
		TagEncoder.encodeTag(ciphertext1, 1L, tagCipher, tagKey);
		IvEncoder.updateIv(iv, 1L);
		ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		frameCipher.doFinal(plaintext1, 0, plaintext1.length, ciphertext1,
				TAG_LENGTH);
		// Use the encryption layer to decrypt the ciphertext
		byte[][] frames = new byte[][] { ciphertext, ciphertext1 };
		SegmentSource in = new ByteArraySegmentSource(frames);
		IncomingEncryptionLayer decrypter =
			new IncomingSegmentedEncryptionLayer(in, tagCipher, frameCipher,
					tagKey, frameKey, true);
		// First frame
		Segment s = new SegmentImpl();
		assertTrue(decrypter.readSegment(s));
		assertEquals(plaintext.length, s.getLength());
		assertEquals(0L, s.getSegmentNumber());
		byte[] decrypted = s.getBuffer();
		for(int i = 0; i < plaintext.length; i++) {
			assertEquals(plaintext[i], decrypted[i]);
		}
		// Second frame
		assertTrue(decrypter.readSegment(s));
		assertEquals(plaintext1.length, s.getLength());
		assertEquals(1L, s.getSegmentNumber());
		decrypted = s.getBuffer();
		for(int i = 0; i < plaintext1.length; i++) {
			assertEquals(plaintext1[i], decrypted[i]);
		}
	}

	private static class ByteArraySegmentSource implements SegmentSource {

		private final byte[][] segments;

		private int segmentNumber = 0;

		private ByteArraySegmentSource(byte[][] frames) {
			this.segments = frames;
		}

		public boolean readSegment(Segment s) throws IOException {
			if(segmentNumber == segments.length) return false;
			byte[] src = segments[segmentNumber];
			System.arraycopy(src, 0, s.getBuffer(), 0, src.length);
			s.setLength(src.length);
			segmentNumber++;
			return true;
		}
	}
}
