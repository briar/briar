package org.briarproject.bramble.plugin.tor;

import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.plugin.BackoffFactory;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.ResourceProvider;
import org.briarproject.bramble.test.BrambleJavaIntegrationTestComponent;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.DaggerBrambleJavaIntegrationTestComponent;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.net.SocketFactory;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@Ignore("Might fail non-deterministically when bridges are down")
public class BridgeTest extends BrambleTestCase {

	private final static long TIMEOUT = SECONDS.toMillis(23);

	private final static Logger LOG =
			Logger.getLogger(BridgeTest.class.getName());

	@Inject
	NetworkManager networkManager;
	@Inject
	ResourceProvider resourceProvider;
	@Inject
	CircumventionProvider circumventionProvider;
	@Inject
	EventBus eventBus;
	@Inject
	BackoffFactory backoffFactory;
	@Inject
	Clock clock;

	private List<String> bridges;
	private LinuxTorPluginFactory factory;
	private final static File torDir = getTestDirectory();

	private volatile String currentBridge = null;

	@Before
	public void setUp() {
		BrambleJavaIntegrationTestComponent component =
				DaggerBrambleJavaIntegrationTestComponent.builder().build();
		component.inject(this);

		Executor ioExecutor = Executors.newCachedThreadPool();
		LocationUtils locationUtils = () -> "US";
		SocketFactory torSocketFactory = SocketFactory.getDefault();

		bridges = circumventionProvider.getBridges();
		CircumventionProvider bridgeProvider = new CircumventionProvider() {
			@Override
			public boolean isTorProbablyBlocked(String countryCode) {
				return true;
			}

			@Override
			public boolean doBridgesWork(String countryCode) {
				return true;
			}

			@Override
			public List<String> getBridges() {
				return singletonList(currentBridge);
			}
		};
		factory = new LinuxTorPluginFactory(ioExecutor, networkManager,
				locationUtils, eventBus, torSocketFactory, backoffFactory,
				resourceProvider, bridgeProvider, clock, torDir);
	}

	@AfterClass
	public static void tearDown() {
		deleteTestDirectory(torDir);
	}

	@Test
	public void testBridges() throws Exception {
		assertTrue(bridges.size() > 0);

		for (String bridge : bridges) testBridge(bridge);
	}

	private void testBridge(String bridge) throws Exception {
		DuplexPlugin duplexPlugin =
				factory.createPlugin(new TorPluginCallBack());
		assertNotNull(duplexPlugin);
		LinuxTorPlugin plugin = (LinuxTorPlugin) duplexPlugin;

		currentBridge = bridge;
		LOG.warning("Testing " + bridge);
		try {
			plugin.start();
			long start = clock.currentTimeMillis();
			while (clock.currentTimeMillis() - start < TIMEOUT) {
				if (plugin.isRunning()) return;
				clock.sleep(500);
			}
			if (!plugin.isRunning()) {
				fail("Could not connect to Tor within timeout.");
			}
		} finally {
			plugin.stop();
		}
	}

}
