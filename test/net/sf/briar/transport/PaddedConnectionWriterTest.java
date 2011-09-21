package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.util.ByteUtils;

import org.junit.Test;

public class PaddedConnectionWriterTest extends TransportTest {

	public PaddedConnectionWriterTest() throws Exception {
		super();
	}

	@Test
	public void testWriteByteDoesNotBlockUntilBufferIsFull() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionEncrypter e = new NullConnectionEncrypter(out);
		ConnectionWriter w = new PaddedConnectionWriter(e, mac);
		final OutputStream out1 = w.getOutputStream();
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean finished = new AtomicBoolean(false);
		final AtomicBoolean failed = new AtomicBoolean(false);
		new Thread() {
			@Override
			public void run() {
				try {
					for(int i = 0; i < maxPayloadLength; i++) out1.write(0);
					finished.set(true);
				} catch(IOException e) {
					failed.set(true);
				}
				latch.countDown();
			}
		}.start();
		// The wait should not time out
		assertTrue(latch.await(1, TimeUnit.SECONDS));
		assertTrue(finished.get());
		assertFalse(failed.get());
		// Nothing should have been written
		assertEquals(0, out.size());
	}

	@Test
	public void testWriteByteBlocksWhenBufferIsFull() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionEncrypter e = new NullConnectionEncrypter(out);
		PaddedConnectionWriter w = new PaddedConnectionWriter(e, mac);
		final OutputStream out1 = w.getOutputStream();
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean finished = new AtomicBoolean(false);
		final AtomicBoolean failed = new AtomicBoolean(false);
		new Thread() {
			@Override
			public void run() {
				try {
					for(int i = 0; i < maxPayloadLength + 1; i++) out1.write(0);
					finished.set(true);
				} catch(IOException e) {
					failed.set(true);
				}
				latch.countDown();
			}
		}.start();
		// The wait should time out
		assertFalse(latch.await(1, TimeUnit.SECONDS));
		assertFalse(finished.get());
		assertFalse(failed.get());
		// Calling writeFullFrame() should allow the writer to proceed
		w.writeFullFrame();
		assertTrue(latch.await(1, TimeUnit.SECONDS));
		assertTrue(finished.get());
		assertFalse(failed.get());
		// A full frame should have been written
		assertEquals(MAX_FRAME_LENGTH, out.size());
	}

	@Test
	public void testWriteArrayDoesNotBlockUntilBufferIsFull() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionEncrypter e = new NullConnectionEncrypter(out);
		ConnectionWriter w = new PaddedConnectionWriter(e, mac);
		final OutputStream out1 = w.getOutputStream();
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean finished = new AtomicBoolean(false);
		final AtomicBoolean failed = new AtomicBoolean(false);
		new Thread() {
			@Override
			public void run() {
				try {
					out1.write(new byte[maxPayloadLength]);
					finished.set(true);
				} catch(IOException e) {
					failed.set(true);
				}
				latch.countDown();
			}
		}.start();
		// The wait should not time out
		assertTrue(latch.await(1, TimeUnit.SECONDS));
		assertTrue(finished.get());
		assertFalse(failed.get());
		// Nothing should have been written
		assertEquals(0, out.size());
	}

	@Test
	public void testWriteArrayBlocksWhenBufferIsFull() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionEncrypter e = new NullConnectionEncrypter(out);
		PaddedConnectionWriter w = new PaddedConnectionWriter(e, mac);
		final OutputStream out1 = w.getOutputStream();
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean finished = new AtomicBoolean(false);
		final AtomicBoolean failed = new AtomicBoolean(false);
		new Thread() {
			@Override
			public void run() {
				try {
					out1.write(new byte[maxPayloadLength + 1]);
					finished.set(true);
				} catch(IOException e) {
					failed.set(true);
				}
				latch.countDown();
			}
		}.start();
		// The wait should time out
		assertFalse(latch.await(1, TimeUnit.SECONDS));
		assertFalse(finished.get());
		assertFalse(failed.get());
		// Calling writeFullFrame() should allow the writer to proceed
		w.writeFullFrame();
		assertTrue(latch.await(1, TimeUnit.SECONDS));
		assertTrue(finished.get());
		assertFalse(failed.get());
		// A full frame should have been written
		assertEquals(MAX_FRAME_LENGTH, out.size());
	}

	@Test
	public void testWriteFullFrameInsertsPadding() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionEncrypter e = new NullConnectionEncrypter(out);
		PaddedConnectionWriter w = new PaddedConnectionWriter(e, mac);
		w.getOutputStream().write(0);
		w.writeFullFrame();
		// A full frame should have been written
		assertEquals(MAX_FRAME_LENGTH, out.size());
		// The frame should have a payload length of 1 and padding for the rest
		byte[] frame = out.toByteArray();
		assertEquals(1, ByteUtils.readUint16(frame, 0)); // Payload length
		assertEquals(maxPayloadLength - 1, ByteUtils.readUint16(frame, 2));
	}

	@Test
	public void testGetCapacity() throws Exception {
		int overheadPerFrame = 4 + mac.getMacLength();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionEncrypter e = new NullConnectionEncrypter(out);
		PaddedConnectionWriter w = new PaddedConnectionWriter(e, mac);
		// Full frame
		long capacity = w.getCapacity(MAX_FRAME_LENGTH);
		assertEquals(MAX_FRAME_LENGTH - overheadPerFrame, capacity);
		// Partial frame
		capacity = w.getCapacity(overheadPerFrame + 1);
		assertEquals(1, capacity);
		// Full frame and partial frame
		capacity = w.getCapacity(MAX_FRAME_LENGTH + 1);
		assertEquals(MAX_FRAME_LENGTH + 1 - 2 * overheadPerFrame, capacity);
		// Buffer some output
		w.getOutputStream().write(0);
		// Full frame minus buffered frame
		capacity = w.getCapacity(MAX_FRAME_LENGTH);
		assertEquals(MAX_FRAME_LENGTH - 1 - 2 * overheadPerFrame, capacity);
		// Flush the buffer
		w.writeFullFrame();
		assertEquals(MAX_FRAME_LENGTH, out.size());
		// Back to square one
		capacity = w.getCapacity(MAX_FRAME_LENGTH);
		assertEquals(MAX_FRAME_LENGTH - overheadPerFrame, capacity);
	}
}
