package org.briarproject.bramble.plugin.tor;

import org.briarproject.bramble.BrambleCoreIntegrationTestEagerSingletons;
import org.briarproject.bramble.api.Multiset;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.concurrent.GuardedBy;
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
		// Share stats among all the test instances
		Stats stats = new Stats();
		CircumventionProvider provider = component.getCircumventionProvider();
		List<Params> states = new ArrayList<>();
		for (int i = 0; i < ATTEMPTS_PER_BRIDGE; i++) {
			for (String bridge : provider.getBridges(DEFAULT_OBFS4)) {
				states.add(new Params(bridge, DEFAULT_OBFS4, stats, false));
			}
			for (String bridge : provider.getBridges(NON_DEFAULT_OBFS4)) {
				states.add(new Params(bridge, NON_DEFAULT_OBFS4, stats, false));
			}
			for (String bridge : provider.getBridges(MEEK)) {
				states.add(new Params(bridge, MEEK, stats, true));
			}
		}
		return states;
	}

	private final static long OBFS4_TIMEOUT = MINUTES.toMillis(2);
	private final static long MEEK_TIMEOUT = MINUTES.toMillis(6);
	private final static int UNREACHABLE_BRIDGES_ALLOWED = 4;
	private final static int ATTEMPTS_PER_BRIDGE = 5;

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
			long timeout = params.bridgeType == MEEK
					? MEEK_TIMEOUT : OBFS4_TIMEOUT;
			while (clock.currentTimeMillis() - start < timeout) {
				if (plugin.getState() == ACTIVE) return;
				clock.sleep(500);
			}
			if (plugin.getState() != ACTIVE) {
				LOG.warning("Could not connect to Tor within timeout: "
						+ params.bridge);
				params.stats.countFailure(params.bridge, params.essential);
			}
		} finally {
			plugin.stop();
		}
	}

	private static class Params {

		private final String bridge;
		private final BridgeType bridgeType;
		private final Stats stats;
		private final boolean essential;

		private Params(String bridge, BridgeType bridgeType,
				Stats stats, boolean essential) {
			this.bridge = bridge;
			this.bridgeType = bridgeType;
			this.stats = stats;
			this.essential = essential;
		}
	}

	private static class Stats {

		@GuardedBy("this")
		private final Multiset<String> failures = new Multiset<>();
		@GuardedBy("this")
		private final Set<String> unreachable = new TreeSet<>();

		private synchronized void countFailure(String bridge,
				boolean essential) {
			if (failures.add(bridge) == ATTEMPTS_PER_BRIDGE) {
				LOG.warning("Bridge is unreachable after "
						+ ATTEMPTS_PER_BRIDGE + " attempts: " + bridge);
				unreachable.add(bridge);
				if (unreachable.size() > UNREACHABLE_BRIDGES_ALLOWED) {
					fail(unreachable.size() + " bridges are unreachable: "
							+ unreachable);
				}
				if (essential) {
					fail("essential bridge is unreachable");
				}
			}
		}
	}
}
