package org.briarproject.sync;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.briarproject.BriarTestCase;
import org.briarproject.TestUtils;
import org.briarproject.api.FormatException;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriter;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.data.DataModule;
import org.briarproject.util.ByteUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.briarproject.api.data.DataConstants.LIST_END_LENGTH;
import static org.briarproject.api.data.DataConstants.UNIQUE_ID_LENGTH;
import static org.briarproject.api.sync.MessagingConstants.HEADER_LENGTH;
import static org.briarproject.api.sync.MessagingConstants.MAX_PAYLOAD_LENGTH;
import static org.briarproject.api.sync.PacketTypes.ACK;
import static org.briarproject.api.sync.PacketTypes.OFFER;
import static org.briarproject.api.sync.PacketTypes.REQUEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PacketReaderImplTest extends BriarTestCase {

	// FIXME: This is an integration test, not a unit test

	private final BdfReaderFactory bdfReaderFactory;
	private final BdfWriterFactory bdfWriterFactory;

	public PacketReaderImplTest() throws Exception {
		Injector i = Guice.createInjector(new DataModule());
		bdfReaderFactory = i.getInstance(BdfReaderFactory.class);
		bdfWriterFactory = i.getInstance(BdfWriterFactory.class);
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfAckIsTooLarge() throws Exception {
		byte[] b = createAck(true);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(bdfReaderFactory, null,
				null, in);
		reader.readAck();
	}

	@Test
	public void testNoFormatExceptionIfAckIsMaximumSize() throws Exception {
		byte[] b = createAck(false);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(bdfReaderFactory, null,
				null, in);
		reader.readAck();
	}

	@Test(expected = FormatException.class)
	public void testEmptyAck() throws Exception {
		byte[] b = createEmptyAck();
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(bdfReaderFactory, null,
				null, in);
		reader.readAck();
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfOfferIsTooLarge() throws Exception {
		byte[] b = createOffer(true);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(bdfReaderFactory, null,
				null, in);
		reader.readOffer();
	}

	@Test
	public void testNoFormatExceptionIfOfferIsMaximumSize() throws Exception {
		byte[] b = createOffer(false);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl
				reader = new PacketReaderImpl(
				bdfReaderFactory, null,
				null, in);
		reader.readOffer();
	}

	@Test(expected = FormatException.class)
	public void testEmptyOffer() throws Exception {
		byte[] b = createEmptyOffer();
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(bdfReaderFactory, null,
				null, in);
		reader.readOffer();
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfRequestIsTooLarge() throws Exception {
		byte[] b = createRequest(true);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(bdfReaderFactory, null,
				null, in);
		reader.readRequest();
	}

	@Test
	public void testNoFormatExceptionIfRequestIsMaximumSize() throws Exception {
		byte[] b = createRequest(false);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(bdfReaderFactory, null,
				null, in);
		reader.readRequest();
	}

	@Test(expected = FormatException.class)
	public void testEmptyRequest() throws Exception {
		byte[] b = createEmptyRequest();
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(bdfReaderFactory, null,
				null, in);
		reader.readRequest();
	}

	private byte[] createAck(boolean tooBig) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(new byte[HEADER_LENGTH]);
		BdfWriter w = bdfWriterFactory.createWriter(out);
		w.writeListStart();
		w.writeListStart();
		while (out.size() + UNIQUE_ID_LENGTH + LIST_END_LENGTH * 2
				< HEADER_LENGTH + MAX_PAYLOAD_LENGTH) {
			w.writeRaw(TestUtils.getRandomId());
		}
		if (tooBig) w.writeRaw(TestUtils.getRandomId());
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
		BdfWriter w = bdfWriterFactory.createWriter(out);
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
		BdfWriter w = bdfWriterFactory.createWriter(out);
		w.writeListStart();
		w.writeListStart();
		while (out.size() + UNIQUE_ID_LENGTH + LIST_END_LENGTH * 2
				< HEADER_LENGTH + MAX_PAYLOAD_LENGTH) {
			w.writeRaw(TestUtils.getRandomId());
		}
		if (tooBig) w.writeRaw(TestUtils.getRandomId());
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
		BdfWriter w = bdfWriterFactory.createWriter(out);
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
		BdfWriter w = bdfWriterFactory.createWriter(out);
		w.writeListStart();
		w.writeListStart();
		while (out.size() + UNIQUE_ID_LENGTH + LIST_END_LENGTH * 2
				< HEADER_LENGTH + MAX_PAYLOAD_LENGTH) {
			w.writeRaw(TestUtils.getRandomId());
		}
		if (tooBig) w.writeRaw(TestUtils.getRandomId());
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
		BdfWriter w = bdfWriterFactory.createWriter(out);
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
