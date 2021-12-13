package org.briarproject.bramble.plugin.tor;

import org.briarproject.bramble.BrambleCoreIntegrationTestEagerSingletons;
import org.briarproject.bramble.api.battery.BatteryManager;
import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.BackoffFactory;
import org.briarproject.bramble.api.plugin.duplex.DuplexPlugin;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.ResourceProvider;
import org.briarproject.bramble.api.system.WakefulIoExecutor;
import org.briarproject.bramble.plugin.tor.CircumventionProvider.BridgeType;
import org.briarproject.bramble.test.BrambleJavaIntegrationTestComponent;
import org.briarproject.bramble.test.BrambleTestCase;
import org.briarproject.bramble.test.DaggerBrambleJavaIntegrationTestComponent;
import org.briarproject.bramble.util.OsUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.net.SocketFactory;

import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.api.plugin.TorConstants.DEFAULT_CONTROL_PORT;
import static org.briarproject.bramble.api.plugin.TorConstants.DEFAULT_SOCKS_PORT;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BridgeType.DEFAULT_OBFS4;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BridgeType.MEEK;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BridgeType.NON_DEFAULT_OBFS4;
import static org.briarproject.bramble.test.TestUtils.deleteTestDirectory;
import static org.briarproject.bramble.test.TestUtils.getTestDirectory;
import static org.briarproject.bramble.test.TestUtils.isOptionalTestEnabled;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class BridgeTest extends BrambleTestCase {

	@Parameters
	public static Iterable<Params> data() {
		BrambleJavaIntegrationTestComponent component =
				DaggerBrambleJavaIntegrationTestComponent.builder().build();
		BrambleCoreIntegrationTestEagerSingletons.Helper
				.injectEagerSingletons(component);
		// Share a failure counter among all the test instances
		AtomicInteger failures = new AtomicInteger(0);
		CircumventionProvider provider = component.getCircumventionProvider();
		List<Params> states = new ArrayList<>();
		for (String bridge : provider.getBridges(DEFAULT_OBFS4)) {
			states.add(new Params(bridge, DEFAULT_OBFS4, failures, false));
		}
		for (String bridge : provider.getBridges(NON_DEFAULT_OBFS4)) {
			states.add(new Params(bridge, NON_DEFAULT_OBFS4, failures, false));
		}
		for (String bridge : provider.getBridges(MEEK)) {
			states.add(new Params(bridge, MEEK, failures, true));
		}
		return states;
	}

	private final static long TIMEOUT = MINUTES.toMillis(5);
	private final static int NUM_FAILURES_ALLOWED = 1;

	private final static Logger LOG = getLogger(BridgeTest.class.getName());

	@Inject
	@IoExecutor
	Executor ioExecutor;
	@Inject
	@WakefulIoExecutor
	Executor wakefulIoExecutor;
	@Inject
	NetworkManager networkManager;
	@Inject
	ResourceProvider resourceProvider;
	@Inject
	CircumventionProvider circumventionProvider;
	@Inject
	BatteryManager batteryManager;
	@Inject
	EventBus eventBus;
	@Inject
	BackoffFactory backoffFactory;
	@Inject
	Clock clock;
	@Inject
	CryptoComponent crypto;

	private final File torDir = getTestDirectory();
	private final Params params;

	private UnixTorPluginFactory factory;

	public BridgeTest(Params params) {
		this.params = params;
	}

	@Before
	public void setUp() {
		// Skip this test unless it's explicitly enabled in the environment
		assumeTrue(isOptionalTestEnabled(BridgeTest.class));

		// TODO: Remove this assumption when the plugin supports other platforms
		assumeTrue(OsUtils.isLinux());

		BrambleJavaIntegrationTestComponent component =
				DaggerBrambleJavaIntegrationTestComponent.builder().build();
		BrambleCoreIntegrationTestEagerSingletons.Helper
				.injectEagerSingletons(component);
		component.inject(this);

		LocationUtils locationUtils = () -> "US";
		SocketFactory torSocketFactory = SocketFactory.getDefault();

		@NotNullByDefault
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
			public BridgeType getBestBridgeType(String countryCode) {
				return params.bridgeType;
			}

			@Override
			public List<String> getBridges(BridgeType bridgeType) {
				return singletonList(params.bridge);
			}
		};
		factory = new UnixTorPluginFactory(ioExecutor, wakefulIoExecutor,
				networkManager, locationUtils, eventBus, torSocketFactory,
				backoffFactory, resourceProvider, bridgeProvider,
				batteryManager, clock, torDir, DEFAULT_SOCKS_PORT,
				DEFAULT_CONTROL_PORT, crypto);
	}

	@After
	public void tearDown() {
		deleteTestDirectory(torDir);
	}

	@Test
	public void testBridges() throws Exception {
		DuplexPlugin duplexPlugin =
				factory.createPlugin(new TestPluginCallback());
		assertNotNull(duplexPlugin);
		UnixTorPlugin plugin = (UnixTorPlugin) duplexPlugin;

		LOG.warning("Testing " + params.bridge);
		try {
			plugin.start();
			long start = clock.currentTimeMillis();
			while (clock.currentTimeMillis() - start < TIMEOUT) {
				if (plugin.getState() == ACTIVE) return;
				clock.sleep(500);
			}
			if (plugin.getState() != ACTIVE) {
				LOG.warning("Could not connect to Tor within timeout");
				if (params.failures.incrementAndGet() > NUM_FAILURES_ALLOWED) {
					fail(params.failures.get() + " bridges are unreachable");
				}
				if (params.mustSucceed) {
					fail("essential bridge is unreachable");
				}
			}
		} finally {
			plugin.stop();
		}
	}

	private static class Params {

		private final String bridge;
		private final BridgeType bridgeType;
		private final AtomicInteger failures;
		private final boolean mustSucceed;

		private Params(String bridge, BridgeType bridgeType,
				AtomicInteger failures, boolean mustSucceed) {
			this.bridge = bridge;
			this.bridgeType = bridgeType;
			this.failures = failures;
			this.mustSucceed = mustSucceed;
		}
	}
}
