package net.sf.briar.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collection;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.Offer;
import net.sf.briar.api.protocol.PacketFactory;
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.SerialComponent;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;
import net.sf.briar.serial.SerialModule;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class OfferReaderTest extends BriarTestCase {

	private final SerialComponent serial;
	private final ReaderFactory readerFactory;
	private final WriterFactory writerFactory;
	private final Mockery context;

	public OfferReaderTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new SerialModule());
		serial = i.getInstance(SerialComponent.class);
		readerFactory = i.getInstance(ReaderFactory.class);
		writerFactory = i.getInstance(WriterFactory.class);
		context = new Mockery();
	}

	@Test
	public void testFormatExceptionIfOfferIsTooLarge() throws Exception {
		PacketFactory packetFactory = context.mock(PacketFactory.class);
		OfferReader offerReader = new OfferReader(packetFactory);

		byte[] b = createOffer(true);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Reader reader = readerFactory.createReader(in);
		reader.addStructReader(Types.OFFER, offerReader);

		try {
			reader.readStruct(Types.OFFER, Offer.class);
			fail();
		} catch(FormatException expected) {}
		context.assertIsSatisfied();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testNoFormatExceptionIfOfferIsMaximumSize() throws Exception {
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		OfferReader offerReader = new OfferReader(packetFactory);
		final Offer offer = context.mock(Offer.class);
		context.checking(new Expectations() {{
			oneOf(packetFactory).createOffer(with(any(Collection.class)));
			will(returnValue(offer));
		}});

		byte[] b = createOffer(false);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Reader reader = readerFactory.createReader(in);
		reader.addStructReader(Types.OFFER, offerReader);

		assertEquals(offer, reader.readStruct(Types.OFFER, Offer.class));
		context.assertIsSatisfied();
	}

	@Test
	public void testEmptyOffer() throws Exception {
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		OfferReader offerReader = new OfferReader(packetFactory);

		byte[] b = createEmptyOffer();
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Reader reader = readerFactory.createReader(in);
		reader.addStructReader(Types.OFFER, offerReader);

		try {
			reader.readStruct(Types.OFFER, Offer.class);
			fail();
		} catch(FormatException expected) {}
		context.assertIsSatisfied();
	}

	private byte[] createOffer(boolean tooBig) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeStructId(Types.OFFER);
		w.writeListStart();
		while(out.size() + serial.getSerialisedUniqueIdLength()
				< ProtocolConstants.MAX_PACKET_LENGTH) {
			w.writeBytes(TestUtils.getRandomId());
		}
		if(tooBig) w.writeBytes(TestUtils.getRandomId());
		w.writeListEnd();
		assertEquals(tooBig, out.size() > ProtocolConstants.MAX_PACKET_LENGTH);
		return out.toByteArray();
	}

	private byte[] createEmptyOffer() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeStructId(Types.OFFER);
		w.writeListStart();
		w.writeListEnd();
		return out.toByteArray();
	}
}
