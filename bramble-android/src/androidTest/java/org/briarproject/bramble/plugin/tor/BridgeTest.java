package org.briarproject.bramble.plugin.tor;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.briarproject.bramble.DaggerIntegrationTestComponent;
import org.briarproject.bramble.IntegrationTestComponent;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.plugin.BackoffFactory;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.net.SocketFactory;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.briarproject.bramble.plugin.tor.TorNetworkMetadata.doBridgesWork;
import static org.briarproject.bramble.plugin.tor.TorNetworkMetadata.isTorProbablyBlocked;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


@RunWith(AndroidJUnit4.class)
public class BridgeTest extends BrambleTestCase {

	private final static String BRIDGE_COUNTRY = "VE";
	private final static long TIMEOUT = SECONDS.toMillis(23);

	private final static Logger LOG =
			Logger.getLogger(BridgeTest.class.getSimpleName());

	@Inject
	EventBus eventBus;
	@Inject
	BackoffFactory backoffFactory;
	@Inject
	Clock clock;

	private final TorPluginFactory factory;
	private TorPlugin plugin;
	private final List<String> bridges;
	private int currentBridge = 0;

	public BridgeTest() {
		IntegrationTestComponent component =
				DaggerIntegrationTestComponent.builder().build();
		component.inject(this);

		Executor ioExecutor = Executors.newCachedThreadPool();
		ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);
		Context appContext = InstrumentationRegistry.getTargetContext();
		LocationUtils locationUtils = () -> BRIDGE_COUNTRY;
		SocketFactory torSocketFactory = SocketFactory.getDefault();
		bridges = new BridgeProviderImpl().getBridges(appContext);
		BridgeProvider bridgeProvider =
				context -> singletonList(bridges.get(currentBridge));
		factory = new TorPluginFactory(ioExecutor, scheduler, appContext,
				locationUtils, eventBus, torSocketFactory,
				backoffFactory, bridgeProvider, clock);
	}

	@Test
	public void testBridges() throws Exception {
		assertTrue(isTorProbablyBlocked(BRIDGE_COUNTRY));
		assertTrue(doBridgesWork(BRIDGE_COUNTRY));
		assertTrue(bridges.size() > 0);

		for (int i = 0; i < bridges.size(); i++) {
			plugin = (TorPlugin) factory.createPlugin(new TorPluginCallBack());
			testBridge(i);
		}
	}

	private void testBridge(int bridge) throws Exception {
		currentBridge = bridge;
		LOG.warning("Testing " + bridges.get(currentBridge));
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
