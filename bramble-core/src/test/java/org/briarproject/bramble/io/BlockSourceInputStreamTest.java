package org.briarproject.bramble.io;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.io.BlockSource;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.jmock.lib.concurrent.Synchroniser;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.concurrent.Executor;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.spongycastle.util.Arrays.copyOfRange;

public class BlockSourceInputStreamTest extends BrambleMockTestCase {

	private static final int MAX_DATA_BYTES = 1_000_000;
	private static final int READ_BUFFER_BYTES = 4 * 1024;
	private static final int BLOCK_BYTES = 32 * 1024;
	private static final int MIN_BUFFER_BYTES = 32 * 1024;

	private final BlockSource blockSource;

	private final Random random = new Random();
	private final Executor executor = newSingleThreadExecutor();
	private final MessageId messageId = new MessageId(getRandomId());

	public BlockSourceInputStreamTest() {
		context.setThreadingPolicy(new Synchroniser());
		blockSource = context.mock(BlockSource.class);
	}

	@Test
	public void testReadSingleBytes() throws IOException {
		byte[] data = createRandomData();
		BlockSource source = new ByteArrayBlockSource(data, BLOCK_BYTES);
		InputStream in = new BlockSourceInputStream(MIN_BUFFER_BYTES, executor,
				source, messageId);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		//noinspection ForLoopReplaceableByForEach
		for (int i = 0; i < data.length; i++) {
			int read = in.read();
			assertNotEquals(-1, read);
			out.write(read);
		}
		assertEquals(-1, in.read());
		in.close();
		out.flush();
		out.close();
		assertArrayEquals(data, out.toByteArray());
	}

	@Test
	public void testReadByteArrays() throws IOException {
		byte[] data = createRandomData();
		BlockSource source = new ByteArrayBlockSource(data, BLOCK_BYTES);
		InputStream in = new BlockSourceInputStream(MIN_BUFFER_BYTES, executor,
				source, messageId);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[READ_BUFFER_BYTES];
		int dataOffset = 0;
		while (dataOffset < data.length) {
			int length = Math.min(random.nextInt(buf.length) + 1,
					data.length - dataOffset);
			int bufOffset = 0;
			if (length < buf.length)
				bufOffset = random.nextInt(buf.length - length);
			int read = in.read(buf, bufOffset, length);
			assertNotEquals(-1, read);
			out.write(buf, bufOffset, read);
			dataOffset += read;
		}
		assertEquals(-1, in.read(buf, 0, 0));
		in.close();
		out.flush();
		out.close();
		assertArrayEquals(data, out.toByteArray());
	}

	@Test(expected = IOException.class)
	public void testDbExceptionFromGetBlockCountIsRethrown() throws Exception {
		context.checking(new Expectations() {{
			oneOf(blockSource).getBlockCount(messageId);
			will(throwException(new DbException()));
		}});

		InputStream in = new BlockSourceInputStream(MIN_BUFFER_BYTES, executor,
				blockSource, messageId);
		//noinspection ResultOfMethodCallIgnored
		in.read();
	}

	@Test(expected = IOException.class)
	public void testDbExceptionFromGetBlockIsRethrown() throws Exception {
		context.checking(new Expectations() {{
			oneOf(blockSource).getBlockCount(messageId);
			will(returnValue(1));
			oneOf(blockSource).getBlock(messageId, 0);
			will(throwException(new DbException()));
		}});

		InputStream in = new BlockSourceInputStream(MIN_BUFFER_BYTES, executor,
				blockSource, messageId);
		//noinspection ResultOfMethodCallIgnored
		in.read();
	}

	@Test
	public void testReadFullBlockAtEndOfMessage() throws Exception {
		testReadBlockAtEndOfMessage(BLOCK_BYTES);
	}

	@Test
	public void testReadPartialBlockAtEndOfMessage() throws Exception {
		testReadBlockAtEndOfMessage(BLOCK_BYTES - 1);
	}

	private void testReadBlockAtEndOfMessage(int blockLength) throws Exception {
		byte[] block = new byte[blockLength];
		random.nextBytes(block);

		context.checking(new Expectations() {{
			oneOf(blockSource).getBlockCount(messageId);
			will(returnValue(1));
			oneOf(blockSource).getBlock(messageId, 0);
			will(returnValue(block));
		}});

		InputStream in = new BlockSourceInputStream(MIN_BUFFER_BYTES, executor,
				blockSource, messageId);
		byte[] buf = new byte[BLOCK_BYTES * 2];
		assertEquals(block.length, in.read(buf, 0, buf.length));
		assertArrayEquals(block, copyOfRange(buf, 0, block.length));
		assertEquals(-1, in.read(buf, 0, buf.length));
	}

	private byte[] createRandomData() {
		int length = random.nextInt(MAX_DATA_BYTES) + 1;
		byte[] data = new byte[length];
		random.nextBytes(data);
		return data;
	}
}
