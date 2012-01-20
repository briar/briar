package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_SEGMENT_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;
import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.plugins.SegmentSink;
import net.sf.briar.api.transport.Segment;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class SegmentedOutgoingEncryptionLayerTest extends BriarTestCase {

	private static final int MAC_LENGTH = 32;

	private final Cipher tagCipher, segCipher;
	private final ErasableKey tagKey, segKey;

	public SegmentedOutgoingEncryptionLayerTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		tagCipher = crypto.getTagCipher();
		segCipher = crypto.getSegmentCipher();
		tagKey = crypto.generateTestKey();
		segKey = crypto.generateTestKey();
	}

	@Test
	public void testEncryptionWithFirstSegmentTagged() throws Exception {
		// Calculate the expected tag
		byte[] tag = new byte[TAG_LENGTH];
		TagEncoder.encodeTag(tag, 0L, tagCipher, tagKey);
		// Calculate the expected ciphertext for the first segment
		byte[] iv = new byte[segCipher.getBlockSize()];
		byte[] plaintext = new byte[123 + MAC_LENGTH];
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		segCipher.init(Cipher.ENCRYPT_MODE, segKey, ivSpec);
		byte[] ciphertext = segCipher.doFinal(plaintext);
		// Calculate the expected ciphertext for the second segment
		byte[] plaintext1 = new byte[1234 + MAC_LENGTH];
		IvEncoder.updateIv(iv, 1L);
		ivSpec = new IvParameterSpec(iv);
		segCipher.init(Cipher.ENCRYPT_MODE, segKey, ivSpec);
		byte[] ciphertext1 = segCipher.doFinal(plaintext1);
		// Concatenate the ciphertexts
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(tag);
		out.write(ciphertext);
		out.write(ciphertext1);
		byte[] expected = out.toByteArray();
		// Use the encryption layer to encrypt the plaintext
		ByteArraySegmentSink sink = new ByteArraySegmentSink();
		OutgoingEncryptionLayer encrypter =
			new SegmentedOutgoingEncryptionLayer(sink, Long.MAX_VALUE,
					tagCipher, segCipher, tagKey, segKey, false);
		Segment s = new SegmentImpl();
		System.arraycopy(plaintext, 0, s.getBuffer(), 0, plaintext.length);
		s.setLength(plaintext.length);
		s.setSegmentNumber(0L);
		encrypter.writeSegment(s);
		System.arraycopy(plaintext1, 0, s.getBuffer(), 0, plaintext1.length);
		s.setLength(plaintext1.length);
		s.setSegmentNumber(1L);
		encrypter.writeSegment(s);
		byte[] actual = out.toByteArray();
		// Check that the actual ciphertext matches the expected ciphertext
		assertArrayEquals(expected, actual);
		assertEquals(Long.MAX_VALUE - actual.length,
				encrypter.getRemainingCapacity());
	}

	@Test
	public void testEncryptionWithEverySegmentTagged() throws Exception {
		// Calculate the expected tag for the first segment
		byte[] tag = new byte[TAG_LENGTH];
		TagEncoder.encodeTag(tag, 0L, tagCipher, tagKey);
		// Calculate the expected ciphertext for the first segment
		byte[] iv = new byte[segCipher.getBlockSize()];
		byte[] plaintext = new byte[123 + MAC_LENGTH];
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		segCipher.init(Cipher.ENCRYPT_MODE, segKey, ivSpec);
		byte[] ciphertext = segCipher.doFinal(plaintext);
		// Calculate the expected tag for the second segment
		byte[] tag1 = new byte[TAG_LENGTH];
		TagEncoder.encodeTag(tag1, 1L, tagCipher, tagKey);
		// Calculate the expected ciphertext for the second segment
		byte[] plaintext1 = new byte[1234 + MAC_LENGTH];
		IvEncoder.updateIv(iv, 1L);
		ivSpec = new IvParameterSpec(iv);
		segCipher.init(Cipher.ENCRYPT_MODE, segKey, ivSpec);
		byte[] ciphertext1 = segCipher.doFinal(plaintext1);
		// Concatenate the ciphertexts
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(tag);
		out.write(ciphertext);
		out.write(tag1);
		out.write(ciphertext1);
		byte[] expected = out.toByteArray();
		// Use the encryption layer to encrypt the plaintext
		SegmentSink sink = new ByteArraySegmentSink();
		OutgoingEncryptionLayer encrypter =
			new SegmentedOutgoingEncryptionLayer(sink, Long.MAX_VALUE,
					tagCipher, segCipher, tagKey, segKey, true);
		Segment s = new SegmentImpl();
		System.arraycopy(plaintext, 0, s.getBuffer(), 0, plaintext.length);
		s.setLength(plaintext.length);
		s.setSegmentNumber(0L);
		encrypter.writeSegment(s);
		System.arraycopy(plaintext1, 0, s.getBuffer(), 0, plaintext1.length);
		s.setLength(plaintext1.length);
		s.setSegmentNumber(1L);
		encrypter.writeSegment(s);
		byte[] actual = out.toByteArray();
		// Check that the actual ciphertext matches the expected ciphertext
		assertArrayEquals(expected, actual);
		assertEquals(Long.MAX_VALUE - actual.length,
				encrypter.getRemainingCapacity());
	}

	private static class ByteArraySegmentSink extends ByteArrayOutputStream
	implements SegmentSink {

		public void writeSegment(Segment s) throws IOException {
			write(s.getBuffer(), 0, s.getLength());
		}

		public int getMaxSegmentLength() {
			return MAX_SEGMENT_LENGTH;
		}
	}
}
