package org.briarproject.bramble.plugin.tor;

import android.content.Context;
import android.support.test.runner.AndroidJUnit4;

import org.briarproject.bramble.DaggerIntegrationTestComponent;
import org.briarproject.bramble.IntegrationTestComponent;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.plugin.BackoffFactory;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Before;
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

import static android.support.test.InstrumentationRegistry.getTargetContext;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


@RunWith(AndroidJUnit4.class)
public class BridgeTest extends BrambleTestCase {

	private final static long TIMEOUT = SECONDS.toMillis(23);

	private final static Logger LOG =
			Logger.getLogger(BridgeTest.class.getSimpleName());

	@Inject
	EventBus eventBus;
	@Inject
	BackoffFactory backoffFactory;
	@Inject
	Clock clock;

	private final Context appContext = getTargetContext();
	private final CircumventionProvider circumventionProvider;
	private final List<String> bridges;
	private TorPluginFactory factory;
	private volatile int currentBridge = 0;

	public BridgeTest() {
		super();
		circumventionProvider = new CircumventionProvider() {
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
				return singletonList(bridges.get(currentBridge));
			}
		};
		bridges = new CircumventionProviderImpl(appContext).getBridges();
	}

	@Before
	public void setUp() {
		IntegrationTestComponent component =
				DaggerIntegrationTestComponent.builder().build();
		component.inject(this);

		Executor ioExecutor = Executors.newCachedThreadPool();
		ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);
		LocationUtils locationUtils = () -> "US";
		SocketFactory torSocketFactory = SocketFactory.getDefault();

		factory = new TorPluginFactory(ioExecutor, scheduler, appContext,
				locationUtils, eventBus, torSocketFactory,
				backoffFactory, circumventionProvider, clock);
	}

	@Test
	public void testBridges() throws Exception {
		assertTrue(bridges.size() > 0);

		for (int i = 0; i < bridges.size(); i++) {
			testBridge(i);
		}
	}

	private void testBridge(int bridge) throws Exception {
		DuplexPlugin duplexPlugin =
				factory.createPlugin(new TorPluginCallBack());
		assertNotNull(duplexPlugin);
		TorPlugin plugin = (TorPlugin) duplexPlugin;

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
