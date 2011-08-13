package net.sf.briar.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import javax.crypto.Mac;

import junit.framework.TestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.transport.PacketWriter;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.util.StringUtils;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class PacketWriterImplTest extends TestCase {

	private final Mac mac;

	public PacketWriterImplTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		mac = crypto.getMac();
		mac.init(crypto.generateSecretKey());
	}

	@Test
	public void testFirstWriteTriggersTag() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PacketEncrypter e = new NullPacketEncrypter(out);
		PacketWriter p = new PacketWriterImpl(e, mac, 0, 0L);
		p.getOutputStream().write(0);
		// There should be TAG_BYTES bytes for the tag, 1 byte for the packet
		assertTrue(Arrays.equals(new byte[Constants.TAG_BYTES + 1],
				out.toByteArray()));
	}

	@Test
	public void testFinishPacketAfterWriteTriggersMac() throws Exception {
		// Calculate what the MAC should be
		mac.update(new byte[Constants.TAG_BYTES + 1]);
		byte[] expectedMac = mac.doFinal();
		// Check that the PacketWriter calculates and writes the correct MAC
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PacketEncrypter e = new NullPacketEncrypter(out);
		PacketWriter p = new PacketWriterImpl(e, mac, 0, 0L);
		p.getOutputStream().write(0);
		p.finishPacket();
		byte[] written = out.toByteArray();
		assertEquals(Constants.TAG_BYTES + 1 + expectedMac.length,
				written.length);
		byte[] actualMac = new byte[expectedMac.length];
		System.arraycopy(written, Constants.TAG_BYTES + 1, actualMac, 0,
				actualMac.length);
		assertTrue(Arrays.equals(expectedMac, actualMac));
	}

	@Test
	public void testExtraCallsToFinishPacketDoNothing() throws Exception {
		// Calculate what the MAC should be
		mac.update(new byte[Constants.TAG_BYTES + 1]);
		byte[] expectedMac = mac.doFinal();
		// Check that the PacketWriter calculates and writes the correct MAC
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PacketEncrypter e = new NullPacketEncrypter(out);
		PacketWriter p = new PacketWriterImpl(e, mac, 0, 0L);
		// Initial calls to finishPacket() should have no effect
		p.finishPacket();
		p.finishPacket();
		p.finishPacket();
		p.getOutputStream().write(0);
		p.finishPacket();
		// Extra calls to finishPacket() should have no effect
		p.finishPacket();
		p.finishPacket();
		p.finishPacket();
		byte[] written = out.toByteArray();
		assertEquals(Constants.TAG_BYTES + 1 + expectedMac.length,
				written.length);
		byte[] actualMac = new byte[expectedMac.length];
		System.arraycopy(written, Constants.TAG_BYTES + 1, actualMac, 0,
				actualMac.length);
		assertTrue(Arrays.equals(expectedMac, actualMac));
	}

	@Test
	public void testPacketNumberIsIncremented() throws Exception {
		byte[] expectedTag = StringUtils.fromHexString(
				"0000" // 16 bits reserved
				+ "F00D" // 16 bits for the transport ID
				+ "DEADBEEF" // 32 bits for the connection number
				+ "00000000" // 32 bits for the packet number
				+ "00000000" // 32 bits for the block number
		);
		assertEquals(Constants.TAG_BYTES, expectedTag.length);
		byte[] expectedTag1 = StringUtils.fromHexString(
				"0000" // 16 bits reserved
				+ "F00D" // 16 bits for the transport ID
				+ "DEADBEEF" // 32 bits for the connection number
				+ "00000001" // 32 bits for the packet number
				+ "00000000" // 32 bits for the block number
		);
		assertEquals(Constants.TAG_BYTES, expectedTag1.length);
		// Calculate what the MAC on the first packet should be
		mac.update(expectedTag);
		mac.update((byte) 0);
		byte[] expectedMac = mac.doFinal();
		// Calculate what the MAC on the second packet should be
		mac.update(expectedTag1);
		mac.update((byte) 0);
		byte[] expectedMac1 = mac.doFinal();
		// Check that the PacketWriter writes the correct tags and MACs
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PacketEncrypter e = new NullPacketEncrypter(out);
		PacketWriter p = new PacketWriterImpl(e, mac, 0xF00D, 0xDEADBEEFL);
		// Packet one
		p.getOutputStream().write(0);
		p.finishPacket();
		// Packet two
		p.getOutputStream().write(0);
		p.finishPacket();
		byte[] written = out.toByteArray();
		assertEquals(Constants.TAG_BYTES + 1 + expectedMac.length
				+ Constants.TAG_BYTES + 1 + expectedMac1.length,
				written.length);
		// Check the first packet's tag
		byte[] actualTag = new byte[Constants.TAG_BYTES];
		System.arraycopy(written, 0, actualTag, 0, Constants.TAG_BYTES);
		assertTrue(Arrays.equals(expectedTag, actualTag));
		// Check the first packet's MAC
		byte[] actualMac = new byte[expectedMac.length];
		System.arraycopy(written, Constants.TAG_BYTES + 1, actualMac, 0,
				actualMac.length);
		assertTrue(Arrays.equals(expectedMac, actualMac));
		// Check the second packet's tag
		byte[] actualTag1 = new byte[Constants.TAG_BYTES];
		System.arraycopy(written, Constants.TAG_BYTES + 1 + expectedMac.length,
				actualTag1, 0, Constants.TAG_BYTES);
		assertTrue(Arrays.equals(expectedTag1, actualTag1));
		// Check the second packet's MAC
		byte[] actualMac1 = new byte[expectedMac1.length];
		System.arraycopy(written, Constants.TAG_BYTES + 1 + expectedMac.length
				+ Constants.TAG_BYTES + 1, actualMac1, 0, actualMac1.length);
		assertTrue(Arrays.equals(expectedMac1, actualMac1));
	}

	/** A PacketEncrypter that performs no encryption. */
	private static class NullPacketEncrypter implements PacketEncrypter {

		private final OutputStream out;

		private NullPacketEncrypter(OutputStream out) {
			this.out = out;
		}

		public OutputStream getOutputStream() {
			return out;
		}

		public void writeTag(byte[] tag) throws IOException {
			out.write(tag);
		}

		public void finishPacket() {}
	}
}
