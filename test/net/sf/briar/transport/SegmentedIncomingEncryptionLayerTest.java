package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_SEGMENT_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.IOException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.plugins.SegmentSource;
import net.sf.briar.api.transport.Segment;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class SegmentedIncomingEncryptionLayerTest extends BriarTestCase {

	private final Cipher tagCipher, segCipher;
	private final ErasableKey tagKey, segKey;

	public SegmentedIncomingEncryptionLayerTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		tagCipher = crypto.getTagCipher();
		segCipher = crypto.getSegmentCipher();
		tagKey = crypto.generateTestKey();
		segKey = crypto.generateTestKey();
	}

	@Test
	public void testDecryptionWithFirstSegmentTagged() throws Exception {
		// Calculate the ciphertext for the first segment, including its tag
		byte[] plaintext = new byte[FRAME_HEADER_LENGTH + 123 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(plaintext, 0L, 123, 0);
		byte[] ciphertext = new byte[TAG_LENGTH + plaintext.length];
		TagEncoder.encodeTag(ciphertext, 0L, tagCipher, tagKey);
		byte[] iv = IvEncoder.encodeIv(0L, segCipher.getBlockSize());
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		segCipher.init(Cipher.ENCRYPT_MODE, segKey, ivSpec);
		segCipher.doFinal(plaintext, 0, plaintext.length, ciphertext,
				TAG_LENGTH);
		// Calculate the ciphertext for the second segment
		byte[] plaintext1 = new byte[FRAME_HEADER_LENGTH + 1234 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(plaintext1, 1L, 1234, 0);
		IvEncoder.updateIv(iv, 1L);
		ivSpec = new IvParameterSpec(iv);
		segCipher.init(Cipher.ENCRYPT_MODE, segKey, ivSpec);
		byte[] ciphertext1 = segCipher.doFinal(plaintext1, 0,
				plaintext1.length);
		// Buffer the first segment and create a source for the second
		Segment buffered = new SegmentImpl();
		System.arraycopy(ciphertext, 0, buffered.getBuffer(), 0,
				ciphertext.length);
		buffered.setLength(ciphertext.length);
		SegmentSource in = new ByteArraySegmentSource(ciphertext1);
		// Use the encryption layer to decrypt the ciphertext
		IncomingEncryptionLayer decrypter =
			new SegmentedIncomingEncryptionLayer(in, tagCipher, segCipher,
					tagKey, segKey, false, false, buffered);
		// First segment
		Segment s = new SegmentImpl();
		assertTrue(decrypter.readSegment(s));
		assertEquals(plaintext.length, s.getLength());
		assertEquals(0L, s.getSegmentNumber());
		byte[] decrypted = s.getBuffer();
		for(int i = 0; i < plaintext.length; i++) {
			assertEquals(plaintext[i], decrypted[i]);
		}
		// Second segment
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
		// Calculate the ciphertext for the first segment, including its tag
		byte[] plaintext = new byte[FRAME_HEADER_LENGTH + 123 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(plaintext, 0L, 123, 0);
		byte[] ciphertext = new byte[TAG_LENGTH + plaintext.length];
		TagEncoder.encodeTag(ciphertext, 0L, tagCipher, tagKey);
		byte[] iv = IvEncoder.encodeIv(0L, segCipher.getBlockSize());
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		segCipher.init(Cipher.ENCRYPT_MODE, segKey, ivSpec);
		segCipher.doFinal(plaintext, 0, plaintext.length, ciphertext,
				TAG_LENGTH);
		// Calculate the ciphertext for the second frame, including its tag
		byte[] plaintext1 = new byte[FRAME_HEADER_LENGTH + 1234 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(plaintext1, 1L, 1234, 0);
		byte[] ciphertext1 = new byte[TAG_LENGTH + plaintext1.length];
		TagEncoder.encodeTag(ciphertext1, 1L, tagCipher, tagKey);
		IvEncoder.updateIv(iv, 1L);
		ivSpec = new IvParameterSpec(iv);
		segCipher.init(Cipher.ENCRYPT_MODE, segKey, ivSpec);
		segCipher.doFinal(plaintext1, 0, plaintext1.length, ciphertext1,
				TAG_LENGTH);
		// Buffer the first segment and create a source for the second
		Segment buffered = new SegmentImpl();
		System.arraycopy(ciphertext, 0, buffered.getBuffer(), 0,
				ciphertext.length);
		buffered.setLength(ciphertext.length);
		SegmentSource in = new ByteArraySegmentSource(ciphertext1);
		// Use the encryption layer to decrypt the ciphertext
		IncomingEncryptionLayer decrypter =
			new SegmentedIncomingEncryptionLayer(in, tagCipher, segCipher,
					tagKey, segKey, true, false, buffered);
		// First segment
		Segment s = new SegmentImpl();
		assertTrue(decrypter.readSegment(s));
		assertEquals(plaintext.length, s.getLength());
		assertEquals(0L, s.getSegmentNumber());
		byte[] decrypted = s.getBuffer();
		for(int i = 0; i < plaintext.length; i++) {
			assertEquals(plaintext[i], decrypted[i]);
		}
		// Second segment
		assertTrue(decrypter.readSegment(s));
		assertEquals(plaintext1.length, s.getLength());
		assertEquals(1L, s.getSegmentNumber());
		decrypted = s.getBuffer();
		for(int i = 0; i < plaintext1.length; i++) {
			assertEquals(plaintext1[i], decrypted[i]);
		}
	}

	private static class ByteArraySegmentSource implements SegmentSource {

		private final byte[] segment;

		private ByteArraySegmentSource(byte[] segment) {
			this.segment = segment;
		}

		public boolean readSegment(Segment s) throws IOException {
			System.arraycopy(segment, 0, s.getBuffer(), 0, segment.length);
			s.setLength(segment.length);
			return true;
		}

		public int getMaxSegmentLength() {
			return MAX_SEGMENT_LENGTH;
		}
	}
}
