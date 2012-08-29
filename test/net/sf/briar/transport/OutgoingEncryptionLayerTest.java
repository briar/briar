package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.ByteArrayOutputStream;

import javax.crypto.Cipher;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.AuthenticatedCipher;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class OutgoingEncryptionLayerTest extends BriarTestCase {

	// FIXME: Write more tests

	private final CryptoComponent crypto;
	private final Cipher tagCipher;
	private final AuthenticatedCipher frameCipher;

	public OutgoingEncryptionLayerTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		crypto = i.getInstance(CryptoComponent.class);
		tagCipher = crypto.getTagCipher();
		frameCipher = crypto.getFrameCipher();
	}

	@Test
	public void testRemainingCapacityWithTag() throws Exception {
		int frameLength = 1024;
		int maxPayloadLength = frameLength - HEADER_LENGTH - MAC_LENGTH;
		long capacity = 10 * frameLength;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		OutgoingEncryptionLayer o = new OutgoingEncryptionLayer(out, capacity,
				tagCipher, frameCipher, crypto.generateTestKey(),
				crypto.generateTestKey(), frameLength);
		// There should be space for nine full frames and one partial frame
		byte[] frame = new byte[frameLength];
		assertEquals(10 * maxPayloadLength - TAG_LENGTH,
				o.getRemainingCapacity());
		// Write nine frames, each containing a partial payload
		for(int i = 9; i > 0; i--) {
			o.writeFrame(frame, 123, false);
			assertEquals(i * maxPayloadLength - TAG_LENGTH,
					o.getRemainingCapacity());
		}
		// Write the final frame, which will not be padded
		o.writeFrame(frame, 123, true);
		int finalFrameLength = HEADER_LENGTH + 123 + MAC_LENGTH;
		assertEquals(maxPayloadLength - TAG_LENGTH - finalFrameLength,
				o.getRemainingCapacity());
	}

	@Test
	public void testRemainingCapacityWithoutTag() throws Exception {
		int frameLength = 1024;
		int maxPayloadLength = frameLength - HEADER_LENGTH - MAC_LENGTH;
		long capacity = 10 * frameLength;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		OutgoingEncryptionLayer o = new OutgoingEncryptionLayer(out, capacity,
				frameCipher, crypto.generateTestKey(), frameLength);
		// There should be space for ten full frames
		assertEquals(10 * maxPayloadLength, o.getRemainingCapacity());
		// Write nine frames, each containing a partial payload
		byte[] frame = new byte[frameLength];
		for(int i = 9; i > 0; i--) {
			o.writeFrame(frame, 123, false);
			assertEquals(i * maxPayloadLength, o.getRemainingCapacity());
		}
		// Write the final frame, which will not be padded
		o.writeFrame(frame, 123, true);
		int finalFrameLength = HEADER_LENGTH + 123 + MAC_LENGTH;
		assertEquals(maxPayloadLength - finalFrameLength,
				o.getRemainingCapacity());
	}

	@Test
	public void testRemainingCapacityLimitedByFrameNumbers() throws Exception {
		int frameLength = 1024;
		int maxPayloadLength = frameLength - HEADER_LENGTH - MAC_LENGTH;
		long capacity = Long.MAX_VALUE;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		OutgoingEncryptionLayer o = new OutgoingEncryptionLayer(out, capacity,
				frameCipher, crypto.generateTestKey(), frameLength);
		// There should be enough frame numbers for 2^32 frames
		assertEquals((1L << 32) * maxPayloadLength, o.getRemainingCapacity());
		// Write a frame containing a partial payload
		byte[] frame = new byte[frameLength];
		o.writeFrame(frame, 123, false);
		// There should be enough frame numbers for 2^32 - 1 frames
		assertEquals(((1L << 32) - 1) * maxPayloadLength,
				o.getRemainingCapacity());
	}
}
