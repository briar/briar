package net.sf.briar.messaging;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.BitSet;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestDatabaseModule;
import net.sf.briar.TestLifecycleModule;
import net.sf.briar.api.messaging.PacketWriter;
import net.sf.briar.api.messaging.Request;
import net.sf.briar.api.serial.SerialComponent;
import net.sf.briar.api.serial.WriterFactory;
import net.sf.briar.clock.ClockModule;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.db.DatabaseModule;
import net.sf.briar.messaging.duplex.DuplexMessagingModule;
import net.sf.briar.messaging.simplex.SimplexMessagingModule;
import net.sf.briar.serial.SerialModule;
import net.sf.briar.transport.TransportModule;
import net.sf.briar.util.StringUtils;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class PacketWriterImplTest extends BriarTestCase {

	// FIXME: This is an integration test, not a unit test

	private final SerialComponent serial;
	private final WriterFactory writerFactory;

	public PacketWriterImplTest() {
		super();
		Injector i = Guice.createInjector(new TestDatabaseModule(),
				new TestLifecycleModule(), new ClockModule(),
				new CryptoModule(), new DatabaseModule(), new MessagingModule(),
				new DuplexMessagingModule(), new SimplexMessagingModule(),
				new SerialModule(), new TransportModule());
		serial = i.getInstance(SerialComponent.class);
		writerFactory = i.getInstance(WriterFactory.class);
	}

	@Test
	public void testWriteBitmapNoPadding() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PacketWriter w = new PacketWriterImpl(serial, writerFactory, out,
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
		w.writeRequest(new Request(b, 16));
		// Short user tag 5, 0 as uint7, short bytes with length 2, 0xD959
		byte[] output = out.toByteArray();
		assertEquals("C5" + "00" + "92" + "D959",
				StringUtils.toHexString(output));
	}

	@Test
	public void testWriteBitmapWithPadding() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PacketWriter w = new PacketWriterImpl(serial, writerFactory, out,
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
		w.writeRequest(new Request(b, 13));
		// Short user tag 5, 3 as uint7, short bytes with length 2, 0x59D8
		byte[] output = out.toByteArray();
		assertEquals("C5" + "03" + "92" + "59D8",
				StringUtils.toHexString(output));
	}
}
