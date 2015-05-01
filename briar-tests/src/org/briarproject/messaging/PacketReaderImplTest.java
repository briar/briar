package org.briarproject.messaging;

import static org.briarproject.api.messaging.MessagingConstants.HEADER_LENGTH;
import static org.briarproject.api.messaging.MessagingConstants.MAX_PAYLOAD_LENGTH;
import static org.briarproject.api.messaging.PacketTypes.ACK;
import static org.briarproject.api.messaging.PacketTypes.OFFER;
import static org.briarproject.api.messaging.PacketTypes.REQUEST;

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
import org.briarproject.util.ByteUtils;
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
		out.write(new byte[HEADER_LENGTH]);
		Writer w = writerFactory.createWriter(out);
		w.writeListStart();
		w.writeListStart();
		while(out.size() + serial.getSerialisedUniqueIdLength()
				+ serial.getSerialisedListEndLength() * 2
				< HEADER_LENGTH + MAX_PAYLOAD_LENGTH) {
			w.writeBytes(TestUtils.getRandomId());
		}
		if(tooBig) w.writeBytes(TestUtils.getRandomId());
		w.writeListEnd();
		w.writeListEnd();
		assertEquals(tooBig, out.size() > HEADER_LENGTH + MAX_PAYLOAD_LENGTH);
		byte[] packet = out.toByteArray();
		packet[1] = ACK;
		ByteUtils.writeUint16(packet.length - HEADER_LENGTH, packet, 2);
		return packet;
	}

	private byte[] createEmptyAck() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(new byte[HEADER_LENGTH]);
		Writer w = writerFactory.createWriter(out);
		w.writeListStart();
		w.writeListStart();
		w.writeListEnd();
		w.writeListEnd();
		byte[] packet = out.toByteArray();
		packet[1] = ACK;
		ByteUtils.writeUint16(packet.length - HEADER_LENGTH, packet, 2);
		return packet;
	}

	private byte[] createOffer(boolean tooBig) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(new byte[HEADER_LENGTH]);
		Writer w = writerFactory.createWriter(out);
		w.writeListStart();
		w.writeListStart();
		while(out.size() + serial.getSerialisedUniqueIdLength()
				+ serial.getSerialisedListEndLength() * 2
				< HEADER_LENGTH + MAX_PAYLOAD_LENGTH) {
			w.writeBytes(TestUtils.getRandomId());
		}
		if(tooBig) w.writeBytes(TestUtils.getRandomId());
		w.writeListEnd();
		w.writeListEnd();
		assertEquals(tooBig, out.size() > HEADER_LENGTH + MAX_PAYLOAD_LENGTH);
		byte[] packet = out.toByteArray();
		packet[1] = OFFER;
		ByteUtils.writeUint16(packet.length - HEADER_LENGTH, packet, 2);
		return packet;
	}

	private byte[] createEmptyOffer() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(new byte[HEADER_LENGTH]);
		Writer w = writerFactory.createWriter(out);
		w.writeListStart();
		w.writeListStart();
		w.writeListEnd();
		w.writeListEnd();
		byte[] packet = out.toByteArray();
		packet[1] = OFFER;
		ByteUtils.writeUint16(packet.length - HEADER_LENGTH, packet, 2);
		return packet;
	}

	private byte[] createRequest(boolean tooBig) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(new byte[HEADER_LENGTH]);
		Writer w = writerFactory.createWriter(out);
		w.writeListStart();
		w.writeListStart();
		while(out.size() + serial.getSerialisedUniqueIdLength()
				+ serial.getSerialisedListEndLength() * 2
				< HEADER_LENGTH + MAX_PAYLOAD_LENGTH) {
			w.writeBytes(TestUtils.getRandomId());
		}
		if(tooBig) w.writeBytes(TestUtils.getRandomId());
		w.writeListEnd();
		w.writeListEnd();
		assertEquals(tooBig, out.size() > HEADER_LENGTH + MAX_PAYLOAD_LENGTH);
		byte[] packet = out.toByteArray();
		packet[1] = REQUEST;
		ByteUtils.writeUint16(packet.length - HEADER_LENGTH, packet, 2);
		return packet;
	}

	private byte[] createEmptyRequest() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(new byte[HEADER_LENGTH]);
		Writer w = writerFactory.createWriter(out);
		w.writeListStart();
		w.writeListStart();
		w.writeListEnd();
		w.writeListEnd();
		byte[] packet = out.toByteArray();
		packet[1] = REQUEST;
		ByteUtils.writeUint16(packet.length - HEADER_LENGTH, packet, 2);
		return packet;
	}
}
