package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.bramble.util.ByteUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.briarproject.bramble.api.sync.PacketTypes.ACK;
import static org.briarproject.bramble.api.sync.PacketTypes.OFFER;
import static org.briarproject.bramble.api.sync.PacketTypes.REQUEST;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_PACKET_PAYLOAD_LENGTH;
import static org.briarproject.bramble.api.sync.SyncConstants.PACKET_HEADER_LENGTH;
import static org.junit.Assert.assertEquals;

public class PacketReaderImplTest extends BrambleTestCase {

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfAckIsTooLarge() throws Exception {
		byte[] b = createAck(true);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(null, in);
		reader.readAck();
	}

	@Test
	public void testNoFormatExceptionIfAckIsMaximumSize() throws Exception {
		byte[] b = createAck(false);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(null, in);
		reader.readAck();
	}

	@Test(expected = FormatException.class)
	public void testEmptyAck() throws Exception {
		byte[] b = createEmptyAck();
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(null, in);
		reader.readAck();
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfOfferIsTooLarge() throws Exception {
		byte[] b = createOffer(true);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(null, in);
		reader.readOffer();
	}

	@Test
	public void testNoFormatExceptionIfOfferIsMaximumSize() throws Exception {
		byte[] b = createOffer(false);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(null, in);
		reader.readOffer();
	}

	@Test(expected = FormatException.class)
	public void testEmptyOffer() throws Exception {
		byte[] b = createEmptyOffer();
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(null, in);
		reader.readOffer();
	}

	@Test(expected = FormatException.class)
	public void testFormatExceptionIfRequestIsTooLarge() throws Exception {
		byte[] b = createRequest(true);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(null, in);
		reader.readRequest();
	}

	@Test
	public void testNoFormatExceptionIfRequestIsMaximumSize() throws Exception {
		byte[] b = createRequest(false);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(null, in);
		reader.readRequest();
	}

	@Test(expected = FormatException.class)
	public void testEmptyRequest() throws Exception {
		byte[] b = createEmptyRequest();
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketReaderImpl reader = new PacketReaderImpl(null, in);
		reader.readRequest();
	}

	private byte[] createAck(boolean tooBig) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(new byte[PACKET_HEADER_LENGTH]);
		while (out.size() + UniqueId.LENGTH <= PACKET_HEADER_LENGTH
				+ MAX_PACKET_PAYLOAD_LENGTH) {
			out.write(TestUtils.getRandomId());
		}
		if (tooBig) out.write(TestUtils.getRandomId());
		assertEquals(tooBig, out.size() > PACKET_HEADER_LENGTH +
				MAX_PACKET_PAYLOAD_LENGTH);
		byte[] packet = out.toByteArray();
		packet[1] = ACK;
		ByteUtils.writeUint16(packet.length - PACKET_HEADER_LENGTH, packet, 2);
		return packet;
	}

	private byte[] createEmptyAck() throws Exception {
		byte[] packet = new byte[PACKET_HEADER_LENGTH];
		packet[1] = ACK;
		ByteUtils.writeUint16(packet.length - PACKET_HEADER_LENGTH, packet, 2);
		return packet;
	}

	private byte[] createOffer(boolean tooBig) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(new byte[PACKET_HEADER_LENGTH]);
		while (out.size() + UniqueId.LENGTH <= PACKET_HEADER_LENGTH
				+ MAX_PACKET_PAYLOAD_LENGTH) {
			out.write(TestUtils.getRandomId());
		}
		if (tooBig) out.write(TestUtils.getRandomId());
		assertEquals(tooBig, out.size() > PACKET_HEADER_LENGTH +
				MAX_PACKET_PAYLOAD_LENGTH);
		byte[] packet = out.toByteArray();
		packet[1] = OFFER;
		ByteUtils.writeUint16(packet.length - PACKET_HEADER_LENGTH, packet, 2);
		return packet;
	}

	private byte[] createEmptyOffer() throws Exception {
		byte[] packet = new byte[PACKET_HEADER_LENGTH];
		packet[1] = OFFER;
		ByteUtils.writeUint16(packet.length - PACKET_HEADER_LENGTH, packet, 2);
		return packet;
	}

	private byte[] createRequest(boolean tooBig) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(new byte[PACKET_HEADER_LENGTH]);
		while (out.size() + UniqueId.LENGTH <= PACKET_HEADER_LENGTH
				+ MAX_PACKET_PAYLOAD_LENGTH) {
			out.write(TestUtils.getRandomId());
		}
		if (tooBig) out.write(TestUtils.getRandomId());
		assertEquals(tooBig, out.size() > PACKET_HEADER_LENGTH +
				MAX_PACKET_PAYLOAD_LENGTH);
		byte[] packet = out.toByteArray();
		packet[1] = REQUEST;
		ByteUtils.writeUint16(packet.length - PACKET_HEADER_LENGTH, packet, 2);
		return packet;
	}

	private byte[] createEmptyRequest() throws Exception {
		byte[] packet = new byte[PACKET_HEADER_LENGTH];
		packet[1] = REQUEST;
		ByteUtils.writeUint16(packet.length - PACKET_HEADER_LENGTH, packet, 2);
		return packet;
	}
}
