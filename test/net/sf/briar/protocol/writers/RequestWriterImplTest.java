package net.sf.briar.protocol.writers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.BitSet;

import junit.framework.TestCase;
import net.sf.briar.api.protocol.OfferId;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.protocol.writers.RequestWriter;
import net.sf.briar.api.serial.WriterFactory;
import net.sf.briar.serial.SerialModule;
import net.sf.briar.util.StringUtils;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class RequestWriterImplTest extends TestCase {

	private final WriterFactory writerFactory;
	private final OfferId offerId;

	public RequestWriterImplTest() {
		super();
		Injector i = Guice.createInjector(new SerialModule());
		writerFactory = i.getInstance(WriterFactory.class);
		offerId = new OfferId(new byte[UniqueId.LENGTH]);
	}

	@Test
	public void testWriteBitmapNoPadding() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		RequestWriter r = new RequestWriterImpl(out, writerFactory);
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
		r.writeRequest(offerId, b, 16);
		// Short user tag 11, short user tag 10, bytes with length 32 as a
		// uint7, 32 zero bytes, short bytes with length 2, 0xD959
		byte[] output = out.toByteArray();
		assertEquals("CB" + "CA" + "F6" + "20"
				+ "00000000000000000000000000000000"
				+ "00000000000000000000000000000000"
				+ "92" + "D959", StringUtils.toHexString(output));
	}

	@Test
	public void testWriteBitmapWithPadding() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		RequestWriter r = new RequestWriterImpl(out, writerFactory);
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
		r.writeRequest(offerId, b, 13);
		// Short user tag 11, short user tag 10, bytes with length 32 as a
		// uint7, 32 zero bytes, short bytes with length 2, 0x59D8
		byte[] output = out.toByteArray();
		assertEquals("CB" + "CA" + "F6" + "20"
				+ "00000000000000000000000000000000"
				+ "00000000000000000000000000000000"
				+ "92" + "59D8", StringUtils.toHexString(output));
	}
}
