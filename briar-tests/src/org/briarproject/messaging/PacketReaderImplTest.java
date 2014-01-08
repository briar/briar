package org.briarproject.messaging;

import static org.briarproject.api.messaging.MessagingConstants.MAX_PACKET_LENGTH;
import static org.briarproject.api.messaging.Types.ACK;
import static org.briarproject.api.messaging.Types.OFFER;
import static org.briarproject.api.messaging.Types.REQUEST;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.FormatException;
import org.briarproject.api.serial.ReaderFactory;
import org.briarproject.api.serial.SerialComponent;
import org.briarproject.api.serial.Writer;
import org.briarproject.api.serial.WriterFactory;
import org.briarproject.serial.SerialModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class PacketReaderImplTest extends BriarTestCase {

	// FIXME: This is an integration test, not a unit test

	private final SerialComponent serial;
	private final ReaderFactory readerFactory;
	private final WriterFactory writerFactory;

	public PacketReaderImplTest() throws Exception {
		Injector i = Guice.createInjector(new SerialModule());
		serial = i.getInstance(SerialComponent.class);
		readerFactory = i.getInstance(ReaderFactory.class);
		writerFactory = i.getInstance(WriterFactory.class);
	}

	@Test
	public void testFormatExceptionIfAckIsTooLarge() throws Exception {
		byte[] b = createAck(true);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(readerFactory, null,
				null, in);
		try {
			reader.readAck();
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testNoFormatExceptionIfAckIsMaximumSize() throws Exception {
		byte[] b = createAck(false);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(readerFactory, null,
				null, in);
		reader.readAck();
	}

	@Test
	public void testEmptyAck() throws Exception {
		byte[] b = createEmptyAck();
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(readerFactory, null,
				null, in);
		try {
			reader.readAck();
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testFormatExceptionIfOfferIsTooLarge() throws Exception {
		byte[] b = createOffer(true);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(readerFactory, null,
				null, in);
		try {
			reader.readOffer();
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testNoFormatExceptionIfOfferIsMaximumSize() throws Exception {
		byte[] b = createOffer(false);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(readerFactory, null,
				null, in);
		reader.readOffer();
	}

	@Test
	public void testEmptyOffer() throws Exception {
		byte[] b = createEmptyOffer();
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(readerFactory, null,
				null, in);
		try {
			reader.readOffer();
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testFormatExceptionIfRequestIsTooLarge() throws Exception {
		byte[] b = createRequest(true);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(readerFactory, null,
				null, in);
		try {
			reader.readRequest();
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testNoFormatExceptionIfRequestIsMaximumSize() throws Exception {
		byte[] b = createRequest(false);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(readerFactory, null,
				null, in);
		reader.readRequest();
	}

	@Test
	public void testEmptyRequest() throws Exception {
		byte[] b = createEmptyRequest();
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(readerFactory, null,
				null, in);
		try {
			reader.readRequest();
			fail();
		} catch(FormatException expected) {}
	}

	private byte[] createAck(boolean tooBig) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeStructStart(ACK);
		w.writeListStart();
		while(out.size() + serial.getSerialisedUniqueIdLength()
				+ serial.getSerialisedListEndLength()
				+ serial.getSerialisedStructEndLength()
				< MAX_PACKET_LENGTH) {
			w.writeBytes(TestUtils.getRandomId());
		}
		if(tooBig) w.writeBytes(TestUtils.getRandomId());
		w.writeListEnd();
		w.writeStructEnd();
		assertEquals(tooBig, out.size() > MAX_PACKET_LENGTH);
		return out.toByteArray();
	}

	private byte[] createEmptyAck() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeStructStart(ACK);
		w.writeListStart();
		w.writeListEnd();
		w.writeStructEnd();
		return out.toByteArray();
	}

	private byte[] createOffer(boolean tooBig) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeStructStart(OFFER);
		w.writeListStart();
		while(out.size() + serial.getSerialisedUniqueIdLength()
				+ serial.getSerialisedListEndLength()
				+ serial.getSerialisedStructEndLength()
				< MAX_PACKET_LENGTH) {
			w.writeBytes(TestUtils.getRandomId());
		}
		if(tooBig) w.writeBytes(TestUtils.getRandomId());
		w.writeListEnd();
		w.writeStructEnd();
		assertEquals(tooBig, out.size() > MAX_PACKET_LENGTH);
		return out.toByteArray();
	}

	private byte[] createEmptyOffer() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeStructStart(OFFER);
		w.writeListStart();
		w.writeListEnd();
		w.writeStructEnd();
		return out.toByteArray();
	}

	private byte[] createRequest(boolean tooBig) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeStructStart(REQUEST);
		w.writeListStart();
		while(out.size() + serial.getSerialisedUniqueIdLength()
				+ serial.getSerialisedListEndLength()
				+ serial.getSerialisedStructEndLength()
				< MAX_PACKET_LENGTH) {
			w.writeBytes(TestUtils.getRandomId());
		}
		if(tooBig) w.writeBytes(TestUtils.getRandomId());
		w.writeListEnd();
		w.writeStructEnd();
		assertEquals(tooBig, out.size() > MAX_PACKET_LENGTH);
		return out.toByteArray();
	}

	private byte[] createEmptyRequest() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeStructStart(REQUEST);
		w.writeListStart();
		w.writeListEnd();
		w.writeStructEnd();
		return out.toByteArray();
	}
}
