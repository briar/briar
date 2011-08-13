package net.sf.briar.transport;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.Mac;

import junit.framework.TestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.transport.PacketReader;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.util.StringUtils;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class PacketReaderImplTest extends TestCase {

	private final Mac mac;

	public PacketReaderImplTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		mac = crypto.getMac();
		mac.init(crypto.generateSecretKey());
	}

	@Test
	public void testFirstReadTriggersTag() throws Exception {
		// TAG_BYTES for the tag, 1 byte for the packet
		byte[] b = new byte[Constants.TAG_BYTES + 1];
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		PacketDecrypter d = new NullPacketDecrypter(in);
		PacketReader p = new PacketReaderImpl(d, mac, 0, 0L);
		// There should be one byte available before EOF
		assertEquals(0, p.getInputStream().read());
		assertEquals(-1, p.getInputStream().read());
	}

	@Test
	public void testFinishPacketAfterReadTriggersMac() throws Exception {
		// TAG_BYTES for the tag, 1 byte for the packet
		byte[] b = new byte[Constants.TAG_BYTES + 1];
		// Calculate the MAC and append it to the packet
		mac.update(b);
		byte[] macBytes = mac.doFinal();
		byte[] b1 = Arrays.copyOf(b, b.length + macBytes.length);
		System.arraycopy(macBytes, 0, b1, b.length, macBytes.length);
		// Check that the PacketReader reads and verifies the MAC
		ByteArrayInputStream in = new ByteArrayInputStream(b1);
		PacketDecrypter d = new NullPacketDecrypter(in);
		PacketReader p = new PacketReaderImpl(d, mac, 0, 0L);
		assertEquals(0, p.getInputStream().read());
		p.finishPacket();
		// Reading the MAC should take us to EOF
		assertEquals(-1, p.getInputStream().read());
	}

	@Test
	public void testModifyingPacketInvalidatesMac() throws Exception {
		// TAG_BYTES for the tag, 1 byte for the packet
		byte[] b = new byte[Constants.TAG_BYTES + 1];
		// Calculate the MAC and append it to the packet
		mac.update(b);
		byte[] macBytes = mac.doFinal();
		byte[] b1 = Arrays.copyOf(b, b.length + macBytes.length);
		System.arraycopy(macBytes, 0, b1, b.length, macBytes.length);
		// Modify the packet
		b1[Constants.TAG_BYTES] = (byte) 1;
		// Check that the PacketReader reads and fails to verify the MAC
		ByteArrayInputStream in = new ByteArrayInputStream(b1);
		PacketDecrypter d = new NullPacketDecrypter(in);
		PacketReader p = new PacketReaderImpl(d, mac, 0, 0L);
		assertEquals(1, p.getInputStream().read());
		try {
			p.finishPacket();
			fail();
		} catch(GeneralSecurityException expected) {}
	}

	@Test
	public void testExtraCallsToFinishPacketDoNothing() throws Exception {
		// TAG_BYTES for the tag, 1 byte for the packet
		byte[] b = new byte[Constants.TAG_BYTES + 1];
		// Calculate the MAC and append it to the packet
		mac.update(b);
		byte[] macBytes = mac.doFinal();
		byte[] b1 = Arrays.copyOf(b, b.length + macBytes.length);
		System.arraycopy(macBytes, 0, b1, b.length, macBytes.length);
		// Check that the PacketReader reads and verifies the MAC
		ByteArrayInputStream in = new ByteArrayInputStream(b1);
		PacketDecrypter d = new NullPacketDecrypter(in);
		PacketReader p = new PacketReaderImpl(d, mac, 0, 0L);
		// Initial calls to finishPacket() should have no effect
		p.finishPacket();
		p.finishPacket();
		p.finishPacket();
		assertEquals(0, p.getInputStream().read());
		p.finishPacket();
		// Extra calls to finishPacket() should have no effect
		p.finishPacket();
		p.finishPacket();
		p.finishPacket();
		// Reading the MAC should take us to EOF
		assertEquals(-1, p.getInputStream().read());
	}

	@Test
	public void testPacketNumberIsIncremented() throws Exception {
		byte[] tag = StringUtils.fromHexString(
				"0000" // 16 bits reserved
				+ "F00D" // 16 bits for the transport ID
				+ "DEADBEEF" // 32 bits for the connection number
				+ "00000000" // 32 bits for the packet number
				+ "00000000" // 32 bits for the block number
		);
		assertEquals(Constants.TAG_BYTES, tag.length);
		byte[] tag1 = StringUtils.fromHexString(
				"0000" // 16 bits reserved
				+ "F00D" // 16 bits for the transport ID
				+ "DEADBEEF" // 32 bits for the connection number
				+ "00000001" // 32 bits for the packet number
				+ "00000000" // 32 bits for the block number
		);
		assertEquals(Constants.TAG_BYTES, tag1.length);
		// Calculate the MAC on the first packet and append it to the packet
		mac.update(tag);
		mac.update((byte) 0);
		byte[] macBytes = mac.doFinal();
		byte[] b = Arrays.copyOf(tag, tag.length + 1 + macBytes.length);
		System.arraycopy(macBytes, 0, b, tag.length + 1, macBytes.length);
		// Calculate the MAC on the second packet and append it to the packet
		mac.update(tag1);
		mac.update((byte) 0);
		byte[] macBytes1 = mac.doFinal();
		byte[] b1 = Arrays.copyOf(tag1, tag1.length + 1 + macBytes1.length);
		System.arraycopy(macBytes1, 0, b1, tag.length + 1, macBytes1.length);
		// Check that the PacketReader accepts the correct tags and MACs
		byte[] b2 = Arrays.copyOf(b, b.length + b1.length);
		System.arraycopy(b1, 0, b2, b.length, b1.length);
		ByteArrayInputStream in = new ByteArrayInputStream(b2);
		PacketDecrypter d = new NullPacketDecrypter(in);
		PacketReader p = new PacketReaderImpl(d, mac, 0xF00D, 0xDEADBEEFL);
		// Packet one
		assertEquals(0, p.getInputStream().read());
		p.finishPacket();
		// Packet two
		assertEquals(0, p.getInputStream().read());
		p.finishPacket();
		// We should be at EOF
		assertEquals(-1, p.getInputStream().read());
	}

	/** A PacketDecrypter that performs no decryption. */
	private static class NullPacketDecrypter implements PacketDecrypter {

		private final InputStream in;

		private NullPacketDecrypter(InputStream in) {
			this.in = in;
		}

		public InputStream getInputStream() {
			return in;
		}

		public byte[] readTag() throws IOException {
			byte[] tag = new byte[Constants.TAG_BYTES];
			int offset = 0;
			while(offset < tag.length) {
				int read = in.read(tag, offset, tag.length - offset);
				if(read == -1) break;
				offset += read;
			}
			if(offset == 0) return null; // EOF between packets is acceptable
			if(offset < tag.length) throw new EOFException();
			return tag;
		}
	}
}
