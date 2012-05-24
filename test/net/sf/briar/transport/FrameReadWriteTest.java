package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;
import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import javax.crypto.Cipher;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.crypto.IvEncoder;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class FrameReadWriteTest extends BriarTestCase {

	private final CryptoComponent crypto;
	private final Cipher tagCipher, frameCipher, framePeekingCipher;
	private final IvEncoder frameIvEncoder, framePeekingIvEncoder;
	private final Random random;
	private final byte[] outSecret;
	private final ErasableKey tagKey, frameKey;

	public FrameReadWriteTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		crypto = i.getInstance(CryptoComponent.class);
		tagCipher = crypto.getTagCipher();
		frameCipher = crypto.getFrameCipher();
		framePeekingCipher = crypto.getFramePeekingCipher();
		frameIvEncoder = crypto.getFrameIvEncoder();
		framePeekingIvEncoder = crypto.getFramePeekingIvEncoder();
		random = new Random();
		// Since we're sending frames to ourselves, we only need outgoing keys
		outSecret = new byte[32];
		random.nextBytes(outSecret);
		tagKey = crypto.deriveTagKey(outSecret, true);
		frameKey = crypto.deriveFrameKey(outSecret, true);
	}

	@Test
	public void testInitiatorWriteAndRead() throws Exception {
		testWriteAndRead(true);
	}

	@Test
	public void testResponderWriteAndRead() throws Exception {
		testWriteAndRead(false);
	}

	private void testWriteAndRead(boolean initiator) throws Exception {
		// Encode the tag
		byte[] tag = new byte[TAG_LENGTH];
		TagEncoder.encodeTag(tag, tagCipher, tagKey);
		// Generate two random frames
		byte[] frame = new byte[12345];
		random.nextBytes(frame);
		byte[] frame1 = new byte[321];
		random.nextBytes(frame1);
		// Copy the keys - the copies will be erased
		ErasableKey tagCopy = tagKey.copy();
		ErasableKey frameCopy = frameKey.copy();
		// Write the frames
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		FrameWriter encryptionOut = new OutgoingEncryptionLayerImpl(out,
				Long.MAX_VALUE, tagCipher, frameCipher, frameIvEncoder, tagCopy,
				frameCopy);
		ConnectionWriter writer = new ConnectionWriterImpl(encryptionOut);
		OutputStream out1 = writer.getOutputStream();
		out1.write(frame);
		out1.flush();
		out1.write(frame1);
		out1.flush();
		// Read the tag back
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		byte[] recoveredTag = new byte[TAG_LENGTH];
		assertEquals(TAG_LENGTH, in.read(recoveredTag));
		assertArrayEquals(tag, recoveredTag);
		assertTrue(TagEncoder.decodeTag(recoveredTag, tagCipher, tagKey));
		// Read the frames back
		FrameReader encryptionIn = new IncomingEncryptionLayerImpl(in,
				tagCipher, frameCipher, framePeekingCipher, frameIvEncoder,
				framePeekingIvEncoder, tagKey, frameKey, false);
		ConnectionReader reader = new ConnectionReaderImpl(encryptionIn);
		InputStream in1 = reader.getInputStream();
		byte[] recovered = new byte[frame.length];
		int offset = 0;
		while(offset < recovered.length) {
			int read = in1.read(recovered, offset, recovered.length - offset);
			if(read == -1) break;
			offset += read;
		}
		assertEquals(recovered.length, offset);
		assertArrayEquals(frame, recovered);
		byte[] recovered1 = new byte[frame1.length];
		offset = 0;
		while(offset < recovered1.length) {
			int read = in1.read(recovered1, offset, recovered1.length - offset);
			if(read == -1) break;
			offset += read;
		}
		assertEquals(recovered1.length, offset);
		assertArrayEquals(frame1, recovered1);
	}
}
