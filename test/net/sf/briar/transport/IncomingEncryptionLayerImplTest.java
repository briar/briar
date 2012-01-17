package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.ByteArrayInputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.transport.Segment;
import net.sf.briar.crypto.CryptoModule;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class IncomingEncryptionLayerImplTest extends BriarTestCase {

	private final Cipher tagCipher, segCipher;
	private final ErasableKey tagKey, segKey;

	public IncomingEncryptionLayerImplTest() {
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
		// Calculate the tag for the first segment
		byte[] tag = new byte[TAG_LENGTH];
		TagEncoder.encodeTag(tag, 0L, tagCipher, tagKey);
		// Calculate the ciphertext for the first segment
		byte[] plaintext = new byte[FRAME_HEADER_LENGTH + 123 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(plaintext, 0L, 123, 0);
		byte[] iv = IvEncoder.encodeIv(0L, segCipher.getBlockSize());
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		segCipher.init(Cipher.ENCRYPT_MODE, segKey, ivSpec);
		byte[] ciphertext = segCipher.doFinal(plaintext, 0, plaintext.length);
		// Calculate the ciphertext for the second segment
		byte[] plaintext1 = new byte[FRAME_HEADER_LENGTH + 1234 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(plaintext1, 1L, 1234, 0);
		IvEncoder.updateIv(iv, 1L);
		ivSpec = new IvParameterSpec(iv);
		segCipher.init(Cipher.ENCRYPT_MODE, segKey, ivSpec);
		byte[] ciphertext1 = segCipher.doFinal(plaintext1, 0,
				plaintext1.length);
		// Concatenate the ciphertexts, excluding the first tag
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(ciphertext);
		out.write(ciphertext1);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		// Use the encryption layer to decrypt the ciphertext
		IncomingEncryptionLayer decrypter = new IncomingEncryptionLayerImpl(in,
				tagCipher, segCipher, tagKey, segKey, false, tag);
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
		// Calculate the tag for the first segment
		byte[] tag = new byte[TAG_LENGTH];
		TagEncoder.encodeTag(tag, 0L, tagCipher, tagKey);
		// Calculate the ciphertext for the first segment
		byte[] plaintext = new byte[FRAME_HEADER_LENGTH + 123 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(plaintext, 0L, 123, 0);
		byte[] iv = IvEncoder.encodeIv(0L, segCipher.getBlockSize());
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		segCipher.init(Cipher.ENCRYPT_MODE, segKey, ivSpec);
		byte[] ciphertext = segCipher.doFinal(plaintext, 0, plaintext.length);
		// Calculate the tag for the second segment
		byte[] tag1 = new byte[TAG_LENGTH];
		TagEncoder.encodeTag(tag1, 1L, tagCipher, tagKey);
		// Calculate the ciphertext for the second segment
		byte[] plaintext1 = new byte[FRAME_HEADER_LENGTH + 1234 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(plaintext1, 1L, 1234, 0);
		IvEncoder.updateIv(iv, 1L);
		ivSpec = new IvParameterSpec(iv);
		segCipher.init(Cipher.ENCRYPT_MODE, segKey, ivSpec);
		byte[] ciphertext1 = segCipher.doFinal(plaintext1, 0,
				plaintext1.length);
		// Concatenate the ciphertexts, excluding the first tag
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(ciphertext);
		out.write(tag1);
		out.write(ciphertext1);
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		// Use the encryption layer to decrypt the ciphertext
		IncomingEncryptionLayer decrypter = new IncomingEncryptionLayerImpl(in,
				tagCipher, segCipher, tagKey, segKey, true, tag);
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
}
