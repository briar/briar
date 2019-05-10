package org.briarproject.bramble.keyagreement;

import org.briarproject.bramble.api.Bytes;
import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.UnsupportedVersionException;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.BdfReader;
import org.briarproject.bramble.api.data.BdfReaderFactory;
import org.briarproject.bramble.api.keyagreement.Payload;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Test;

import java.io.ByteArrayInputStream;

import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.BETA_PROTOCOL_VERSION;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.COMMIT_LENGTH;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.PROTOCOL_VERSION;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PayloadParserImplTest extends BrambleMockTestCase {

	private final BdfReaderFactory bdfReaderFactory =
			context.mock(BdfReaderFactory.class);
	private final BdfReader bdfReader = context.mock(BdfReader.class);

	private final PayloadParserImpl payloadParser =
			new PayloadParserImpl(bdfReaderFactory);

	@Test(expected = FormatException.class)
	public void testThrowsFormatExceptionIfPayloadIsEmpty() throws Exception {
		payloadParser.parse(new byte[0]);
	}

	@Test
	public void testThrowsUnsupportedVersionExceptionForOldVersion()
			throws Exception {
		try {
			payloadParser.parse(new byte[] {PROTOCOL_VERSION - 1});
			fail();
		} catch (UnsupportedVersionException e) {
			assertTrue(e.isTooOld());
		}
	}

	@Test
	public void testThrowsUnsupportedVersionExceptionForBetaVersion()
			throws Exception {
		try {
			payloadParser.parse(new byte[] {BETA_PROTOCOL_VERSION});
			fail();
		} catch (UnsupportedVersionException e) {
			assertTrue(e.isTooOld());
		}
	}

	@Test
	public void testThrowsUnsupportedVersionExceptionForNewVersion()
			throws Exception {
		try {
			payloadParser.parse(new byte[] {PROTOCOL_VERSION + 1});
			fail();
		} catch (UnsupportedVersionException e) {
			assertFalse(e.isTooOld());
		}
	}

	@Test(expected = FormatException.class)
	public void testThrowsFormatExceptionForEmptyList() throws Exception {
		context.checking(new Expectations() {{
			oneOf(bdfReaderFactory).createReader(
					with(any(ByteArrayInputStream.class)));
			will(returnValue(bdfReader));
			oneOf(bdfReader).readList();
			will(returnValue(new BdfList()));
		}});

		payloadParser.parse(new byte[] {PROTOCOL_VERSION});
	}

	@Test(expected = FormatException.class)
	public void testThrowsFormatExceptionForDataAfterList()
			throws Exception {
		byte[] commitment = getRandomBytes(COMMIT_LENGTH);

		context.checking(new Expectations() {{
			oneOf(bdfReaderFactory).createReader(
					with(any(ByteArrayInputStream.class)));
			will(returnValue(bdfReader));
			oneOf(bdfReader).readList();
			will(returnValue(BdfList.of(new Bytes(commitment))));
			oneOf(bdfReader).eof();
			will(returnValue(false));
		}});

		payloadParser.parse(new byte[] {PROTOCOL_VERSION});
	}

	@Test(expected = FormatException.class)
	public void testThrowsFormatExceptionForShortCommitment()
			throws Exception {
		byte[] commitment = getRandomBytes(COMMIT_LENGTH - 1);

		context.checking(new Expectations() {{
			oneOf(bdfReaderFactory).createReader(
					with(any(ByteArrayInputStream.class)));
			will(returnValue(bdfReader));
			oneOf(bdfReader).readList();
			will(returnValue(BdfList.of(new Bytes(commitment))));
			oneOf(bdfReader).eof();
			will(returnValue(true));
		}});

		payloadParser.parse(new byte[] {PROTOCOL_VERSION});
	}

	@Test(expected = FormatException.class)
	public void testThrowsFormatExceptionForLongCommitment()
			throws Exception {
		byte[] commitment = getRandomBytes(COMMIT_LENGTH + 1);

		context.checking(new Expectations() {{
			oneOf(bdfReaderFactory).createReader(
					with(any(ByteArrayInputStream.class)));
			will(returnValue(bdfReader));
			oneOf(bdfReader).readList();
			will(returnValue(BdfList.of(new Bytes(commitment))));
			oneOf(bdfReader).eof();
			will(returnValue(true));
		}});

		payloadParser.parse(new byte[] {PROTOCOL_VERSION});
	}

	@Test
	public void testAcceptsPayloadWithNoDescriptors() throws Exception {
		byte[] commitment = getRandomBytes(COMMIT_LENGTH);
		context.checking(new Expectations() {{
			oneOf(bdfReaderFactory).createReader(
					with(any(ByteArrayInputStream.class)));
			will(returnValue(bdfReader));
			oneOf(bdfReader).readList();
			will(returnValue(BdfList.of(new Bytes(commitment))));
			oneOf(bdfReader).eof();
			will(returnValue(true));
		}});

		Payload p = payloadParser.parse(new byte[] {PROTOCOL_VERSION});
		assertArrayEquals(commitment, p.getCommitment());
		assertTrue(p.getTransportDescriptors().isEmpty());
	}
}
