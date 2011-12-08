package net.sf.briar.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.BitSet;

import junit.framework.TestCase;
import net.sf.briar.api.protocol.PacketFactory;
import net.sf.briar.api.protocol.ProtocolWriter;
import net.sf.briar.api.protocol.Request;
import net.sf.briar.api.serial.SerialComponent;
import net.sf.briar.api.serial.WriterFactory;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.serial.SerialModule;
import net.sf.briar.util.StringUtils;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ProtocolWriterImplTest extends TestCase {

	private final PacketFactory packetFactory;
	private final SerialComponent serial;
	private final WriterFactory writerFactory;

	public ProtocolWriterImplTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule(),
				new ProtocolModule(), new SerialModule());
		packetFactory = i.getInstance(PacketFactory.class);
		serial = i.getInstance(SerialComponent.class);
		writerFactory = i.getInstance(WriterFactory.class);
	}

	@Test
	public void testWriteBitmapNoPadding() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ProtocolWriter w = new ProtocolWriterImpl(serial, writerFactory, out,
				true);
		BitSet b = new BitSet();
		// 11011001 = 0xD9
		b.set(0);
		b.set(1);
		b.set(3);
		b.set(4);
		b.set(7);
		// 01011001 = 0x59
		b.set(9);
		b.set(11);
		b.set(12);
		b.set(15);
		Request r = packetFactory.createRequest(b, 16);
		w.writeRequest(r);
		// Short user tag 6, 0 as uint7, short bytes with length 2, 0xD959
		byte[] output = out.toByteArray();
		assertEquals("C6" + "00" + "92" + "D959",
				StringUtils.toHexString(output));
	}

	@Test
	public void testWriteBitmapWithPadding() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ProtocolWriter w = new ProtocolWriterImpl(serial, writerFactory, out,
				true);
		BitSet b = new BitSet();
		// 01011001 = 0x59
		b.set(1);
		b.set(3);
		b.set(4);
		b.set(7);
		// 11011xxx = 0xD8, after padding
		b.set(8);
		b.set(9);
		b.set(11);
		b.set(12);
		Request r = packetFactory.createRequest(b, 13);
		w.writeRequest(r);
		// Short user tag 6, 3 as uint7, short bytes with length 2, 0x59D8
		byte[] output = out.toByteArray();
		assertEquals("C6" + "03" + "92" + "59D8",
				StringUtils.toHexString(output));
	}
}
