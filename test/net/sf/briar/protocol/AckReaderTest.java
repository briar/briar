package net.sf.briar.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collection;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.Ack;
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

public class AckReaderTest extends BriarTestCase {

	private final SerialComponent serial;
	private final ReaderFactory readerFactory;
	private final WriterFactory writerFactory;
	private final Mockery context;

	public AckReaderTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new SerialModule());
		serial = i.getInstance(SerialComponent.class);
		readerFactory = i.getInstance(ReaderFactory.class);
		writerFactory = i.getInstance(WriterFactory.class);
		context = new Mockery();
	}

	@Test
	public void testFormatExceptionIfAckIsTooLarge() throws Exception {
		PacketFactory packetFactory = context.mock(PacketFactory.class);
		AckReader ackReader = new AckReader(packetFactory);

		byte[] b = createAck(true);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Reader reader = readerFactory.createReader(in);
		reader.addStructReader(Types.ACK, ackReader);

		try {
			reader.readStruct(Types.ACK, Ack.class);
			fail();
		} catch(FormatException expected) {}
		context.assertIsSatisfied();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testNoFormatExceptionIfAckIsMaximumSize() throws Exception {
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		AckReader ackReader = new AckReader(packetFactory);
		final Ack ack = context.mock(Ack.class);
		context.checking(new Expectations() {{
			oneOf(packetFactory).createAck(with(any(Collection.class)));
			will(returnValue(ack));
		}});

		byte[] b = createAck(false);
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Reader reader = readerFactory.createReader(in);
		reader.addStructReader(Types.ACK, ackReader);

		assertEquals(ack, reader.readStruct(Types.ACK, Ack.class));
		context.assertIsSatisfied();
	}

	@Test
	public void testEmptyAck() throws Exception {
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		AckReader ackReader = new AckReader(packetFactory);

		byte[] b = createEmptyAck();
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Reader reader = readerFactory.createReader(in);
		reader.addStructReader(Types.ACK, ackReader);

		try {
			reader.readStruct(Types.ACK, Ack.class);
			fail();
		} catch(FormatException expected) {}
		context.assertIsSatisfied();
	}

	private byte[] createAck(boolean tooBig) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeStructId(Types.ACK);
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

	private byte[] createEmptyAck() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeStructId(Types.ACK);
		w.writeListStart();
		w.writeListEnd();
		return out.toByteArray();
	}
}
