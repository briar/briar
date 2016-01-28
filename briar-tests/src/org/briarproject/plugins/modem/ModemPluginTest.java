package org.briarproject.plugins.modem;

import org.briarproject.BriarTestCase;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.plugins.duplex.DuplexPluginCallback;
import org.briarproject.api.properties.TransportProperties;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ModemPluginTest extends BriarTestCase {

	private static final String ISO_1336 = "GB";
	private static final String NUMBER = "0123456789";

	@Test
	public void testModemCreation() throws Exception {
		Mockery context = new Mockery();
		final ModemFactory modemFactory = context.mock(ModemFactory.class);
		final SerialPortList serialPortList =
				context.mock(SerialPortList.class);
		final ModemPlugin plugin = new ModemPlugin(modemFactory,
				serialPortList, null, 0);
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
		final ModemPlugin plugin = new ModemPlugin(modemFactory,
				serialPortList, callback, 0);
		final Modem modem = context.mock(Modem.class);
		final TransportProperties local = new TransportProperties();
		local.put("iso3166", ISO_1336);
		TransportProperties p = new TransportProperties();
		p.put("iso3166", ISO_1336);
		p.put("number", NUMBER);
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
			oneOf(modem).dial(NUMBER);
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
		final ModemPlugin plugin = new ModemPlugin(modemFactory,
				serialPortList, callback, 0);
		final Modem modem = context.mock(Modem.class);
		final TransportProperties local = new TransportProperties();
		local.put("iso3166", ISO_1336);
		TransportProperties p = new TransportProperties();
		p.put("iso3166", ISO_1336);
		p.put("number", NUMBER);
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
			oneOf(modem).dial(NUMBER);
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
		final ModemPlugin plugin = new ModemPlugin(modemFactory,
				serialPortList, callback, 0);
		final Modem modem = context.mock(Modem.class);
		final TransportProperties local = new TransportProperties();
		local.put("iso3166", ISO_1336);
		TransportProperties p = new TransportProperties();
		p.put("iso3166", ISO_1336);
		p.put("number", NUMBER);
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
			oneOf(modem).dial(NUMBER);
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
}
