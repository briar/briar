package net.sf.briar.transport;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.transport.Segment;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

public class IncomingErrorCorrectionLayerImplTest extends BriarTestCase {

	@Test
	public void testDiscardedSegmentsAreCounted() throws Exception {
		LinkedList<Long> segmentNumbers = new LinkedList<Long>();
		segmentNumbers.add(123L); // 123 / 3 = frame number 41
		segmentNumbers.add(456L); // 456 / 3 = frame number 152
		segmentNumbers.add(0L); // 0 / 3 = frame number 0
		IncomingEncryptionLayer in = new TestIncomingEncryptionLayer(
				segmentNumbers, 1234);
		Mockery context = new Mockery();
		final ErasureDecoder decoder = context.mock(ErasureDecoder.class);
		final FrameWindow window = context.mock(FrameWindow.class);
		context.checking(new Expectations() {{
			// First segment
			one(window).contains(41L);
			will(returnValue(false));
			one(window).isTooHigh(41L);
			will(returnValue(true));
			// Second segment
			one(window).contains(152L);
			will(returnValue(false));
			one(window).isTooHigh(152L);
			will(returnValue(true));
			// Third segment
			one(window).contains(0L);
			will(returnValue(true));
			one(decoder).decodeFrame(with(any(Frame.class)),
					with(any(Segment[].class)));
			will(returnValue(false));
		}});

		IncomingErrorCorrectionLayerImpl err =
			new IncomingErrorCorrectionLayerImpl(in, decoder, 3, 2);
		Frame f = new Frame();
		assertFalse(err.readFrame(f, window));
		Map<Long, Integer> discardCounts = err.getDiscardCounts();
		assertEquals(2, discardCounts.size());
		assertEquals(Integer.valueOf(1), discardCounts.get(41L));
		assertEquals(Integer.valueOf(1), discardCounts.get(152L));

		context.assertIsSatisfied();
	}

	@Test
	public void testTooManyDiscardedSegmentsCauseException() throws Exception {
		LinkedList<Long> segmentNumbers = new LinkedList<Long>();
		segmentNumbers.add(123L); // 123 / 3 = frame number 41
		segmentNumbers.add(124L); // 124 / 3 = frame number 41
		IncomingEncryptionLayer in = new TestIncomingEncryptionLayer(
				segmentNumbers, 1234);
		Mockery context = new Mockery();
		final ErasureDecoder decoder = context.mock(ErasureDecoder.class);
		final FrameWindow window = context.mock(FrameWindow.class);
		context.checking(new Expectations() {{
			// First segment
			one(window).contains(41L);
			will(returnValue(false));
			one(window).isTooHigh(41L);
			will(returnValue(true));
			// Second segment
			one(window).contains(41L);
			will(returnValue(false));
			one(window).isTooHigh(41L);
			will(returnValue(true));
		}});
		IncomingErrorCorrectionLayerImpl err =
			new IncomingErrorCorrectionLayerImpl(in, decoder, 3, 2);
		Frame f = new Frame();
		try {
			err.readFrame(f, window);
			fail();
		} catch(FormatException expected) {}

		context.assertIsSatisfied();
	}

	@Test
	public void testSetsAndDiscardedSegmentsAreFreed() throws Exception {
		LinkedList<Long> segmentNumbers = new LinkedList<Long>();
		segmentNumbers.add(96L); // 96 / 3 = frame number 32
		segmentNumbers.add(0L); // 0 / 3 = frame number 0
		segmentNumbers.add(1L); // 1 / 3 = frame number 0
		IncomingEncryptionLayer in = new TestIncomingEncryptionLayer(
				segmentNumbers, 1234);
		Mockery context = new Mockery();
		final ErasureDecoder decoder = context.mock(ErasureDecoder.class);
		final FrameWindow window = context.mock(FrameWindow.class);
		context.checking(new Expectations() {{
			// First segment
			one(window).contains(32L);
			will(returnValue(false));
			one(window).isTooHigh(32L);
			will(returnValue(true));
			// Second segment
			one(window).contains(0L);
			will(returnValue(true));
			one(decoder).decodeFrame(with(any(Frame.class)),
					with(any(Segment[].class)));
			will(returnValue(false));
			// Third segment
			one(window).contains(0L);
			will(returnValue(true));
			one(decoder).decodeFrame(with(any(Frame.class)),
					with(any(Segment[].class)));
			will(returnValue(true));
			// Second call, new window
			one(window).contains(0L);
			will(returnValue(false));
			one(window).isTooHigh(32L);
			will(returnValue(false));
		}});
		IncomingErrorCorrectionLayerImpl err =
			new IncomingErrorCorrectionLayerImpl(in, decoder, 3, 2);
		Frame f = new Frame();
		// The first call discards one segment and decodes two
		assertTrue(err.readFrame(f, window));
		// The second call reaches EOF
		assertFalse(err.readFrame(f, window));
		// The segment set and discard count should have been freed
		Map<Long, Segment[]> segmentSets = err.getSegmentSets();
		assertTrue(segmentSets.isEmpty());
		Map<Long, Integer> discardCounts = err.getDiscardCounts();
		assertTrue(discardCounts.isEmpty());

		context.assertIsSatisfied();
	}

	private static class TestIncomingEncryptionLayer
	implements IncomingEncryptionLayer {

		private final LinkedList<Long> segmentNumbers;
		private final int length;

		private TestIncomingEncryptionLayer(LinkedList<Long> segmentNumbers,
				int length) {
			this.segmentNumbers = segmentNumbers;
			this.length = length;
		}

		public boolean readSegment(Segment s) throws IOException,
		InvalidDataException {
			Long segmentNumber = segmentNumbers.poll();
			if(segmentNumber == null) return false;
			s.setSegmentNumber(segmentNumber);
			s.setLength(length);
			return true;
		}
	}
}
