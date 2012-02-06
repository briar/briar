package net.sf.briar.plugins.tor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.duplex.DuplexPluginCallback;
import net.sf.briar.api.plugins.duplex.DuplexTransportConnection;

import org.junit.Test;

public class TorPluginTest extends BriarTestCase {

	@Test
	public void testCreateHiddenService() throws Exception {
		Callback callback = new Callback();
		Executor e = Executors.newCachedThreadPool();
		TorPlugin plugin = new TorPlugin(e, callback, 0L);
		plugin.start();
		// The plugin should have created a hidden service
		callback.latch.await(5, TimeUnit.MINUTES);
		String onion = callback.local.get("onion");
		assertNotNull(onion);
		assertTrue(onion.endsWith(".onion"));
	}

	private static class Callback implements DuplexPluginCallback {

		private final Map<ContactId, TransportProperties> remote =
			new HashMap<ContactId, TransportProperties>();
		private final CountDownLatch latch = new CountDownLatch(1);

		private TransportConfig config = new TransportConfig();
		private TransportProperties local = new TransportProperties();

		public TransportConfig getConfig() {
			return config;
		}

		public TransportProperties getLocalProperties() {
			return local;
		}

		public Map<ContactId, TransportProperties> getRemoteProperties() {
			return remote;
		}

		public void setConfig(TransportConfig c) {
			config = c;
		}

		public void setLocalProperties(TransportProperties p) {
			latch.countDown();
			local = p;
		}

		public int showChoice(String[] options, String... message) {
			return -1;
		}

		public boolean showConfirmationMessage(String... message) {
			return false;
		}

		public void showMessage(String... message) {}

		public void incomingConnectionCreated(DuplexTransportConnection d) {}

		public void outgoingConnectionCreated(ContactId c,
				DuplexTransportConnection d) {}
	}
}
