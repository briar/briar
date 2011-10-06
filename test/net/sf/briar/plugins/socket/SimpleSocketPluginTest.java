package net.sf.briar.plugins.socket;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executor;

import junit.framework.TestCase;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.transport.stream.StreamTransportCallback;
import net.sf.briar.api.transport.stream.StreamTransportConnection;

import org.junit.Before;
import org.junit.Test;

public class SimpleSocketPluginTest extends TestCase {

	private final ContactId contactId = new ContactId(0);

	private Map<String, String> localProperties = null;
	private Map<ContactId, Map<String, String>> remoteProperties = null;
	private Map<String, String> config = null;

	@Before
	public void setUp() {
		localProperties = new TreeMap<String, String>();
		remoteProperties = new HashMap<ContactId, Map<String, String>>();
		remoteProperties.put(contactId, new TreeMap<String, String>());
		config = new TreeMap<String, String>();
	}

	@Test
	public void testBind() throws Exception {
		StubCallback callback = new StubCallback();
		localProperties.put("host", "127.0.0.1");
		localProperties.put("port", "0");
		SimpleSocketPlugin plugin =
			new SimpleSocketPlugin(new ImmediateExecutor(), 10);
		plugin.start(localProperties, remoteProperties, config, callback);
		assertNotNull(callback.localProperties);
		String host = callback.localProperties.get("host");
		assertNotNull(host);
		assertEquals("127.0.0.1", host);
		String port = callback.localProperties.get("port");
		assertNotNull(port);
		assertTrue(Integer.valueOf(port) > 0 && Integer.valueOf(port) < 65536);
		plugin.stop();
	}

	private static class ImmediateExecutor implements Executor {

		public void execute(Runnable r) {
			r.run();
		}
	}

	private static class StubCallback implements StreamTransportCallback {

		private Map<String, String> localProperties = null;

		public void setLocalProperties(Map<String, String> properties) {
			localProperties = properties;
		}

		public void setConfig(Map<String, String> config) {
		}

		public void showMessage(String... message) {
		}

		public boolean showConfirmationMessage(String... message) {
			return false;
		}

		public int showChoice(String[] choices, String... message) {
			return -1;
		}

		public void incomingConnectionCreated(StreamTransportConnection c) {
		}

		public void outgoingConnectionCreated(ContactId contactId,
				StreamTransportConnection c) {
		}
	}
}
