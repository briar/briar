package net.sf.briar.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Random;

import junit.framework.TestCase;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.protocol.Ack;
import net.sf.briar.api.protocol.BatchId;
import net.sf.briar.api.protocol.PacketFactory;
import net.sf.briar.api.protocol.ProtocolConstants;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.UniqueId;
import net.sf.briar.api.serial.Reader;
import net.sf.briar.api.serial.ReaderFactory;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;
import net.sf.briar.serial.SerialModule;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class AckReaderTest extends TestCase {

	private final ReaderFactory readerFactory;
	private final WriterFactory writerFactory;
	private final Mockery context;

	public AckReaderTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new SerialModule());
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
		reader.addObjectReader(Types.ACK, ackReader);

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
		reader.addObjectReader(Types.ACK, ackReader);

		assertEquals(ack, reader.readStruct(Types.ACK, Ack.class));
		context.assertIsSatisfied();
	}

	@Test
	public void testEmptyAck() throws Exception {
		final PacketFactory packetFactory = context.mock(PacketFactory.class);
		AckReader ackReader = new AckReader(packetFactory);
		final Ack ack = context.mock(Ack.class);
		context.checking(new Expectations() {{
			oneOf(packetFactory).createAck(
					with(Collections.<BatchId>emptyList()));
			will(returnValue(ack));
		}});

		byte[] b = createEmptyAck();
		ByteArrayInputStream in = new ByteArrayInputStream(b);
		Reader reader = readerFactory.createReader(in);
		reader.addObjectReader(Types.ACK, ackReader);

		assertEquals(ack, reader.readStruct(Types.ACK, Ack.class));
		context.assertIsSatisfied();
	}

	private byte[] createAck(boolean tooBig) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Writer w = writerFactory.createWriter(out);
		w.writeStructId(Types.ACK);
		w.writeListStart();
		byte[] b = new byte[UniqueId.LENGTH];
		Random random = new Random();
		while(out.size() + BatchId.LENGTH + 3
				< ProtocolConstants.MAX_PACKET_LENGTH) {
			random.nextBytes(b);
			w.writeBytes(b);
		}
		if(tooBig) {
			random.nextBytes(b);
			w.writeBytes(b);
		}
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
