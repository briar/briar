package org.briarproject.bramble.plugin.modem;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.plugin.duplex.DuplexPluginCallback;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.test.BrambleTestCase;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ModemPluginTest extends BrambleTestCase {

	private static final String ISO_1336 = "GB";
	private static final String NUMBER = "0123456789";

	@Test
	public void testModemCreation() throws Exception {
		Mockery context = new Mockery();
		ModemFactory modemFactory = context.mock(ModemFactory.class);
		SerialPortList serialPortList =
				context.mock(SerialPortList.class);
		ModemPlugin plugin = new ModemPlugin(modemFactory,
				serialPortList, null, 0);
		Modem modem = context.mock(Modem.class);
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
		plugin.start();
		context.assertIsSatisfied();
	}

	@Test
	public void testCreateConnection() throws Exception {
		Mockery context = new Mockery();
		ModemFactory modemFactory = context.mock(ModemFactory.class);
		SerialPortList serialPortList =
				context.mock(SerialPortList.class);
		DuplexPluginCallback callback =
				context.mock(DuplexPluginCallback.class);
		ModemPlugin plugin = new ModemPlugin(modemFactory,
				serialPortList, callback, 0);
		Modem modem = context.mock(Modem.class);
		TransportProperties local = new TransportProperties();
		local.put("iso3166", ISO_1336);
		TransportProperties remote = new TransportProperties();
		remote.put("iso3166", ISO_1336);
		remote.put("number", NUMBER);
		ContactId contactId = new ContactId(234);
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
			oneOf(callback).getRemoteProperties(contactId);
			will(returnValue(remote));
			oneOf(modem).dial(NUMBER);
			will(returnValue(true));
		}});
		plugin.start();
		// A connection should be returned
		assertNotNull(plugin.createConnection(contactId));
		context.assertIsSatisfied();
	}

	@Test
	public void testCreateConnectionWhenDialReturnsFalse() throws Exception {
		Mockery context = new Mockery();
		ModemFactory modemFactory = context.mock(ModemFactory.class);
		SerialPortList serialPortList =
				context.mock(SerialPortList.class);
		DuplexPluginCallback callback =
				context.mock(DuplexPluginCallback.class);
		ModemPlugin plugin = new ModemPlugin(modemFactory,
				serialPortList, callback, 0);
		Modem modem = context.mock(Modem.class);
		TransportProperties local = new TransportProperties();
		local.put("iso3166", ISO_1336);
		TransportProperties remote = new TransportProperties();
		remote.put("iso3166", ISO_1336);
		remote.put("number", NUMBER);
		ContactId contactId = new ContactId(234);
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
			oneOf(callback).getRemoteProperties(contactId);
			will(returnValue(remote));
			oneOf(modem).dial(NUMBER);
			will(returnValue(false));
		}});
		plugin.start();
		// No connection should be returned
		assertNull(plugin.createConnection(contactId));
		context.assertIsSatisfied();
	}

	@Test
	public void testCreateConnectionWhenDialThrowsException() throws Exception {
		Mockery context = new Mockery();
		ModemFactory modemFactory = context.mock(ModemFactory.class);
		SerialPortList serialPortList =
				context.mock(SerialPortList.class);
		DuplexPluginCallback callback =
				context.mock(DuplexPluginCallback.class);
		ModemPlugin plugin = new ModemPlugin(modemFactory,
				serialPortList, callback, 0);
		Modem modem = context.mock(Modem.class);
		TransportProperties local = new TransportProperties();
		local.put("iso3166", ISO_1336);
		TransportProperties remote = new TransportProperties();
		remote.put("iso3166", ISO_1336);
		remote.put("number", NUMBER);
		ContactId contactId = new ContactId(234);
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
			oneOf(callback).getRemoteProperties(contactId);
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
		plugin.start();
		// No connection should be returned
		assertNull(plugin.createConnection(contactId));
		context.assertIsSatisfied();
	}
}
