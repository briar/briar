package net.sf.briar.protocol;

import static net.sf.briar.api.protocol.ProtocolConstants.MAX_PACKET_LENGTH;
import static net.sf.briar.api.protocol.Types.OFFER;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.SerialComponent;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;
import net.sf.briar.serial.SerialModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class OfferReaderTest extends BriarTestCase {

	// FIXME: This is an integration test, not a unit test

	private final SerialComponent serial;
	private final ReaderFactory readerFactory;
	private final WriterFactory writerFactory;

	public OfferReaderTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new SerialModule());
		serial = i.getInstance(SerialComponent.class);
		readerFactory = i.getInstance(ReaderFactory.class);
		writerFactory = i.getInstance(WriterFactory.class);
	}

	@Test
	public void testFormatExceptionIfOfferIsTooLarge() throws Exception {
		byte[] b = createOffer(true);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Reader reader = readerFactory.createReader(in);
		reader.addStructReader(OFFER, new OfferReader());
		try {
			reader.readStruct(OFFER, Offer.class);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testNoFormatExceptionIfOfferIsMaximumSize() throws Exception {
		byte[] b = createOffer(false);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Reader reader = readerFactory.createReader(in);
		reader.addStructReader(OFFER, new OfferReader());
		reader.readStruct(OFFER, Offer.class);
	}

	@Test
	public void testEmptyOffer() throws Exception {
		byte[] b = createEmptyOffer();
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Reader reader = readerFactory.createReader(in);
		reader.addStructReader(OFFER, new OfferReader());
		try {
			reader.readStruct(OFFER, Offer.class);
			fail();
		} catch(FormatException expected) {}
	}

	private byte[] createOffer(boolean tooBig) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeStructId(OFFER);
		w.writeListStart();
		while(out.size() + serial.getSerialisedUniqueIdLength()
				< MAX_PACKET_LENGTH) {
			w.writeBytes(TestUtils.getRandomId());
		}
		if(tooBig) w.writeBytes(TestUtils.getRandomId());
		w.writeListEnd();
		assertEquals(tooBig, out.size() > MAX_PACKET_LENGTH);
		return out.toByteArray();
	}

	private byte[] createEmptyOffer() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeStructId(OFFER);
		w.writeListStart();
		w.writeListEnd();
		return out.toByteArray();
	}
}
