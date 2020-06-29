package org.briarproject.bramble.plugin.modem;

import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.properties.TransportProperties;
import org.briarproject.bramble.test.BrambleMockTestCase;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.api.plugin.Plugin.State.ENABLING;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ModemPluginTest extends BrambleMockTestCase {

	private static final String ISO_1336 = "GB";
	private static final String NUMBER = "0123456789";

	private final ModemFactory modemFactory = context.mock(ModemFactory.class);
	private final SerialPortList serialPortList =
			context.mock(SerialPortList.class);
	private final PluginCallback callback = context.mock(PluginCallback.class);
	private final Modem modem = context.mock(Modem.class);

	private ModemPlugin plugin;

	@Before
	public void setUp() {
		plugin = new ModemPlugin(modemFactory, serialPortList, callback, 0);
	}

	@Test
	public void testModemCreation() throws Exception {
		context.checking(new Expectations() {{
			oneOf(callback).pluginStateChanged(ENABLING);
			oneOf(serialPortList).getPortNames();
			will(returnValue(new String[] {"foo", "bar", "baz"}));
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
			oneOf(callback).pluginStateChanged(ACTIVE);
		}});

		plugin.start();
	}

	@Test
	public void testCreateConnection() throws Exception {
		TransportProperties local = new TransportProperties();
		local.put("iso3166", ISO_1336);
		TransportProperties remote = new TransportProperties();
		remote.put("iso3166", ISO_1336);
		remote.put("number", NUMBER);

		context.checking(new Expectations() {{
			// start()
			oneOf(callback).pluginStateChanged(ENABLING);
			oneOf(serialPortList).getPortNames();
			will(returnValue(new String[] {"foo"}));
			oneOf(modemFactory).createModem(plugin, "foo");
			will(returnValue(modem));
			oneOf(modem).start();
			will(returnValue(true));
			oneOf(callback).pluginStateChanged(ACTIVE);
			// createConnection()
			oneOf(callback).getLocalProperties();
			will(returnValue(local));
			oneOf(modem).dial(NUMBER);
			will(returnValue(true));
		}});

		plugin.start();
		// A connection should be returned
		assertNotNull(plugin.createConnection(remote));
	}

	@Test
	public void testCreateConnectionWhenDialReturnsFalse() throws Exception {
		TransportProperties local = new TransportProperties();
		local.put("iso3166", ISO_1336);
		TransportProperties remote = new TransportProperties();
		remote.put("iso3166", ISO_1336);
		remote.put("number", NUMBER);

		context.checking(new Expectations() {{
			// start()
			oneOf(callback).pluginStateChanged(ENABLING);
			oneOf(serialPortList).getPortNames();
			will(returnValue(new String[] {"foo"}));
			oneOf(modemFactory).createModem(plugin, "foo");
			will(returnValue(modem));
			oneOf(modem).start();
			will(returnValue(true));
			oneOf(callback).pluginStateChanged(ACTIVE);
			// createConnection()
			oneOf(callback).getLocalProperties();
			will(returnValue(local));
			oneOf(modem).dial(NUMBER);
			will(returnValue(false));
		}});

		plugin.start();
		// No connection should be returned
		assertNull(plugin.createConnection(remote));
	}

	@Test
	public void testCreateConnectionWhenDialThrowsException() throws Exception {
		TransportProperties local = new TransportProperties();
		local.put("iso3166", ISO_1336);
		TransportProperties remote = new TransportProperties();
		remote.put("iso3166", ISO_1336);
		remote.put("number", NUMBER);

		context.checking(new Expectations() {{
			// start()
			oneOf(callback).pluginStateChanged(ENABLING);
			oneOf(serialPortList).getPortNames();
			will(returnValue(new String[] {"foo"}));
			oneOf(modemFactory).createModem(plugin, "foo");
			will(returnValue(modem));
			oneOf(modem).start();
			will(returnValue(true));
			oneOf(callback).pluginStateChanged(ACTIVE);
			// createConnection()
			oneOf(callback).getLocalProperties();
			will(returnValue(local));
			oneOf(modem).dial(NUMBER);
			will(throwException(new IOException()));
			// resetModem()
			oneOf(serialPortList).getPortNames();
			will(returnValue(new String[] {"foo"}));
			oneOf(modemFactory).createModem(plugin, "foo");
			will(returnValue(modem));
			oneOf(modem).start();
			will(returnValue(true));
		}});

		plugin.start();
		// No connection should be returned
		assertNull(plugin.createConnection(remote));
	}
}
