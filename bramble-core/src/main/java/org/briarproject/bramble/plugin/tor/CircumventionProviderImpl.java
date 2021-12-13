package org.briarproject.bramble.plugin.tor;

import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static java.util.Arrays.asList;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BridgeType.DEFAULT_OBFS4;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BridgeType.MEEK;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BridgeType.NON_DEFAULT_OBFS4;

@Immutable
@NotNullByDefault
class CircumventionProviderImpl implements CircumventionProvider {

	private final static String BRIDGE_FILE_NAME = "bridges";

	private static final Set<String> BLOCKED_IN_COUNTRIES =
			new HashSet<>(asList(BLOCKED));
	private static final Set<String> BRIDGE_COUNTRIES =
			new HashSet<>(asList(BRIDGES));
	private static final Set<String> DEFAULT_OBFS4_BRIDGE_COUNTRIES =
			new HashSet<>(asList(DEFAULT_OBFS4_BRIDGES));
	private static final Set<String> NON_DEFAULT_OBFS4_BRIDGE_COUNTRIES =
			new HashSet<>(asList(NON_DEFAULT_OBFS4_BRIDGES));
	private static final Set<String> MEEK_COUNTRIES =
			new HashSet<>(asList(MEEK_BRIDGES));

	@Inject
	CircumventionProviderImpl() {
	}

	@Override
	public boolean isTorProbablyBlocked(String countryCode) {
		return BLOCKED_IN_COUNTRIES.contains(countryCode);
	}

	@Override
	public boolean doBridgesWork(String countryCode) {
		return BRIDGE_COUNTRIES.contains(countryCode);
	}

	@Override
	public BridgeType getBestBridgeType(String countryCode) {
		if (DEFAULT_OBFS4_BRIDGE_COUNTRIES.contains(countryCode)) {
			return DEFAULT_OBFS4;
		} else if (NON_DEFAULT_OBFS4_BRIDGE_COUNTRIES.contains(countryCode)) {
			return NON_DEFAULT_OBFS4;
		} else if (MEEK_COUNTRIES.contains(countryCode)) {
			return MEEK;
		} else {
			return DEFAULT_OBFS4;
		}
	}

	@Override
	@IoExecutor
	public List<String> getBridges(BridgeType type) {
		InputStream is = requireNonNull(getClass().getClassLoader()
				.getResourceAsStream(BRIDGE_FILE_NAME));
		Scanner scanner = new Scanner(is);

		List<String> bridges = new ArrayList<>();
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			boolean isDefaultObfs4 = line.startsWith("d ");
			boolean isNonDefaultObfs4 = line.startsWith("n ");
			boolean isMeek = line.startsWith("m ");
			if ((type == DEFAULT_OBFS4 && isDefaultObfs4) ||
					(type == NON_DEFAULT_OBFS4 && isNonDefaultObfs4) ||
					(type == MEEK && isMeek)) {
				bridges.add(line.substring(2));
			}
		}
		scanner.close();
		return bridges;
	}

}
