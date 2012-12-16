package net.sf.briar.plugins.modem;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;

import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.junit.Test;

public class ModemPluginTest extends BriarTestCase {

	private static final String ISO_1336 = "GB";
	private static final String NUMBER1 = "0123";
	private static final String NUMBER2 = "0234";
	private static final String NUMBER3 = "0345";

	@Test
	public void testModemCreation() throws Exception {
		Mockery context = new Mockery();
		final ModemFactory modemFactory = context.mock(ModemFactory.class);
		final SerialPortList serialPortList =
				context.mock(SerialPortList.class);
		final ModemPlugin plugin = new ModemPlugin(null, modemFactory,
				serialPortList, null, 0L);
		final Modem modem = context.mock(Modem.class);
		context.checking(new Expectations() {{
			oneOf(serialPortList).getPortNames();
			will(returnValue(new String[] { "foo", "bar", "baz" }));
			// First call to createModem() returns false
			oneOf(modemFactory).createModem(plugin, "foo");
			will(returnValue(modem));
			oneOf(modem).start();
			will(returnValue(false));
			// Second call to createModem() throws an exception
			oneOf(modemFactory).createModem(plugin, "bar");
			will(returnValue(modem));
			oneOf(modem).start();
			will(throwException(new IOException()));
			// Third call to createModem() returns true
			oneOf(modemFactory).createModem(plugin, "baz");
			will(returnValue(modem));
			oneOf(modem).start();
			will(returnValue(true));
		}});
		assertTrue(plugin.start());
		context.assertIsSatisfied();
	}

	@Test
	public void testCreateConnection() throws Exception {
		Mockery context = new Mockery();
		final ModemFactory modemFactory = context.mock(ModemFactory.class);
		final SerialPortList serialPortList =
				context.mock(SerialPortList.class);
		final DuplexPluginCallback callback =
				context.mock(DuplexPluginCallback.class);
		final ModemPlugin plugin = new ModemPlugin(null, modemFactory,
				serialPortList, callback, 0L);
		final Modem modem = context.mock(Modem.class);
		final TransportProperties local = new TransportProperties();
		local.put("iso3166", ISO_1336);
		TransportProperties p = new TransportProperties();
		p.put("iso3166", ISO_1336);
		p.put("number", NUMBER1);
		ContactId contactId = new ContactId(234);
		final Map<ContactId, TransportProperties> remote =
				Collections.singletonMap(contactId, p);
		context.checking(new Expectations() {{
			// start()
			oneOf(serialPortList).getPortNames();
			will(returnValue(new String[] { "foo" }));
			oneOf(modemFactory).createModem(plugin, "foo");
			will(returnValue(modem));
			oneOf(modem).start();
			will(returnValue(true));
			// createConnection()
			oneOf(callback).getLocalProperties();
			will(returnValue(local));
			oneOf(callback).getRemoteProperties();
			will(returnValue(remote));
			oneOf(modem).dial(NUMBER1);
			will(returnValue(true));
		}});
		assertTrue(plugin.start());
		// A connection should be returned
		assertNotNull(plugin.createConnection(contactId));
		context.assertIsSatisfied();
	}

	@Test
	public void testCreateConnectionWhenDialReturnsFalse() throws Exception {
		Mockery context = new Mockery();
		final ModemFactory modemFactory = context.mock(ModemFactory.class);
		final SerialPortList serialPortList =
				context.mock(SerialPortList.class);
		final DuplexPluginCallback callback =
				context.mock(DuplexPluginCallback.class);
		final ModemPlugin plugin = new ModemPlugin(null, modemFactory,
				serialPortList, callback, 0L);
		final Modem modem = context.mock(Modem.class);
		final TransportProperties local = new TransportProperties();
		local.put("iso3166", ISO_1336);
		TransportProperties p = new TransportProperties();
		p.put("iso3166", ISO_1336);
		p.put("number", NUMBER1);
		ContactId contactId = new ContactId(234);
		final Map<ContactId, TransportProperties> remote =
				Collections.singletonMap(contactId, p);
		context.checking(new Expectations() {{
			// start()
			oneOf(serialPortList).getPortNames();
			will(returnValue(new String[] { "foo" }));
			oneOf(modemFactory).createModem(plugin, "foo");
			will(returnValue(modem));
			oneOf(modem).start();
			will(returnValue(true));
			// createConnection()
			oneOf(callback).getLocalProperties();
			will(returnValue(local));
			oneOf(callback).getRemoteProperties();
			will(returnValue(remote));
			oneOf(modem).dial(NUMBER1);
			will(returnValue(false));
		}});
		assertTrue(plugin.start());
		// No connection should be returned
		assertNull(plugin.createConnection(contactId));
		context.assertIsSatisfied();
	}

	@Test
	public void testCreateConnectionWhenDialThrowsException() throws Exception {
		Mockery context = new Mockery();
		final ModemFactory modemFactory = context.mock(ModemFactory.class);
		final SerialPortList serialPortList =
				context.mock(SerialPortList.class);
		final DuplexPluginCallback callback =
				context.mock(DuplexPluginCallback.class);
		final ModemPlugin plugin = new ModemPlugin(null, modemFactory,
				serialPortList, callback, 0L);
		final Modem modem = context.mock(Modem.class);
		final TransportProperties local = new TransportProperties();
		local.put("iso3166", ISO_1336);
		TransportProperties p = new TransportProperties();
		p.put("iso3166", ISO_1336);
		p.put("number", NUMBER1);
		ContactId contactId = new ContactId(234);
		final Map<ContactId, TransportProperties> remote =
				Collections.singletonMap(contactId, p);
		context.checking(new Expectations() {{
			// start()
			oneOf(serialPortList).getPortNames();
			will(returnValue(new String[] { "foo" }));
			oneOf(modemFactory).createModem(plugin, "foo");
			will(returnValue(modem));
			oneOf(modem).start();
			will(returnValue(true));
			// createConnection()
			oneOf(callback).getLocalProperties();
			will(returnValue(local));
			oneOf(callback).getRemoteProperties();
			will(returnValue(remote));
			oneOf(modem).dial(NUMBER1);
			will(throwException(new IOException()));
			// resetModem()
			oneOf(serialPortList).getPortNames();
			will(returnValue(new String[] { "foo" }));
			oneOf(modemFactory).createModem(plugin, "foo");
			will(returnValue(modem));
			oneOf(modem).start();
			will(returnValue(true));
		}});
		assertTrue(plugin.start());
		// No connection should be returned
		assertNull(plugin.createConnection(contactId));
		context.assertIsSatisfied();
	}

	@Test
	public void testPolling() throws Exception {
		final ExecutorService pluginExecutor =
				Executors.newSingleThreadExecutor();
		Mockery context = new Mockery();
		final ModemFactory modemFactory = context.mock(ModemFactory.class);
		final SerialPortList serialPortList =
				context.mock(SerialPortList.class);
		final DuplexPluginCallback callback =
				context.mock(DuplexPluginCallback.class);
		final ModemPlugin plugin = new ModemPlugin(pluginExecutor, modemFactory,
				serialPortList, callback, 0L);
		final Modem modem = context.mock(Modem.class);
		final TransportProperties local = new TransportProperties();
		local.put("iso3166", ISO_1336);
		TransportProperties p1 = new TransportProperties();
		p1.put("iso3166", ISO_1336);
		p1.put("number", NUMBER1);
		TransportProperties p2 = new TransportProperties();
		p2.put("iso3166", ISO_1336);
		p2.put("number", NUMBER2);
		TransportProperties p3 = new TransportProperties();
		p3.put("iso3166", ISO_1336);
		p3.put("number", NUMBER3);
		ContactId contactId1 = new ContactId(234);
		ContactId contactId2 = new ContactId(345);
		ContactId contactId3 = new ContactId(456);
		final Map<ContactId, TransportProperties> remote =
				new HashMap<ContactId, TransportProperties>();
		remote.put(contactId1, p1);
		remote.put(contactId2, p2);
		remote.put(contactId3, p3);
		final DisposeAction disposeAction = new DisposeAction();
		context.checking(new Expectations() {{
			// start()
			oneOf(serialPortList).getPortNames();
			will(returnValue(new String[] { "foo" }));
			oneOf(modemFactory).createModem(plugin, "foo");
			will(returnValue(modem));
			oneOf(modem).start();
			will(returnValue(true));
			// poll()
			oneOf(callback).getLocalProperties();
			will(returnValue(local));
			oneOf(callback).getRemoteProperties();
			will(returnValue(remote));
			// First call to dial() throws an exception
			oneOf(modem).dial(NUMBER1);
			will(throwException(new IOException()));
			// resetModem()
			oneOf(serialPortList).getPortNames();
			will(returnValue(new String[] { "foo" }));
			oneOf(modemFactory).createModem(plugin, "foo");
			will(returnValue(modem));
			oneOf(modem).start();
			will(returnValue(true));
			// Second call to dial() returns true
			oneOf(modem).dial(NUMBER2);
			will(returnValue(true));
			// A connection is passed to the callback - dispose of it
			oneOf(callback).outgoingConnectionCreated(
					with(any(ContactId.class)),
					with(any(DuplexTransportConnection.class)));
			will(disposeAction);
			oneOf(modem).hangUp();
			// Third call to dial() returns false
			oneOf(modem).dial(NUMBER3);
			will(returnValue(false));
		}});
		assertTrue(plugin.start());
		plugin.poll(Collections.<ContactId>emptyList());
		assertTrue(disposeAction.invoked.await(5, SECONDS));
		pluginExecutor.shutdown();
		context.assertIsSatisfied();
	}

	private static class DisposeAction implements Action {

		private final CountDownLatch invoked = new CountDownLatch(1);

		public void describeTo(Description description) {
			description.appendText("Disposes of a transport connection");
		}

		public Object invoke(Invocation invocation) throws Throwable {
			DuplexTransportConnection conn =
					(DuplexTransportConnection) invocation.getParameter(1);
			conn.dispose(false, true);
			invoked.countDown();
			return null;
		}
	}
}
