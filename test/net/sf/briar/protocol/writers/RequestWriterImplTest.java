package net.sf.briar.protocol.writers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.BitSet;

import junit.framework.TestCase;
import net.sf.briar.api.protocol.writers.RequestWriter;
import net.sf.briar.api.serial.WriterFactory;
import net.sf.briar.serial.SerialModule;
import net.sf.briar.util.StringUtils;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class RequestWriterImplTest extends TestCase {

	private final WriterFactory writerFactory;

	public RequestWriterImplTest() {
		super();
		Injector i = Guice.createInjector(new SerialModule());
		writerFactory = i.getInstance(WriterFactory.class);
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
		r.writeRequest(b, 16);
		// Short user tag 8, short bytes with length 2, 0xD959
		byte[] output = out.toByteArray();
		assertEquals("C8" + "92" + "D959", StringUtils.toHexString(output));
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
		r.writeRequest(b, 13);
		// Short user tag 8, short bytes with length 2, 0x59D8
		byte[] output = out.toByteArray();
		assertEquals("C8" + "92" + "59D8", StringUtils.toHexString(output));
	}
}
