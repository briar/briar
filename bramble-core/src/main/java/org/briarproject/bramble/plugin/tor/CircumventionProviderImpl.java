package org.briarproject.bramble.plugin.tor;

import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.nullsafety.NotNullByDefault;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static java.util.Arrays.asList;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BridgeType.DEFAULT_OBFS4;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BridgeType.MEEK;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BridgeType.NON_DEFAULT_OBFS4;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BridgeType.SNOWFLAKE;
import static org.briarproject.bramble.plugin.tor.CircumventionProvider.BridgeType.VANILLA;
import static org.briarproject.nullsafety.NullSafety.requireNonNull;

@Immutable
@NotNullByDefault
class CircumventionProviderImpl implements CircumventionProvider {

	private final static String BRIDGE_FILE_NAME = "bridges";
	private final static String SNOWFLAKE_PARAMS_FILE_NAME = "snowflake-params";
	private final static String DEFAULT_COUNTRY_CODE = "ZZ";

	private static final Set<String> BLOCKED_IN_COUNTRIES =
			new HashSet<>(asList(BLOCKED));
	private static final Set<String> BRIDGE_COUNTRIES =
			new HashSet<>(asList(BRIDGES));
	private static final Set<String> DEFAULT_OBFS4_BRIDGE_COUNTRIES =
			new HashSet<>(asList(DEFAULT_BRIDGES));
	private static final Set<String> NON_DEFAULT_BRIDGE_COUNTRIES =
			new HashSet<>(asList(NON_DEFAULT_BRIDGES));
	private static final Set<String> DPI_COUNTRIES =
			new HashSet<>(asList(DPI_BRIDGES));

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
	public List<BridgeType> getSuitableBridgeTypes(String countryCode) {
		if (DEFAULT_OBFS4_BRIDGE_COUNTRIES.contains(countryCode)) {
			return asList(DEFAULT_OBFS4, VANILLA);
		} else if (NON_DEFAULT_BRIDGE_COUNTRIES.contains(countryCode)) {
			return asList(NON_DEFAULT_OBFS4, VANILLA);
		} else if (DPI_COUNTRIES.contains(countryCode)) {
			return asList(NON_DEFAULT_OBFS4, MEEK, SNOWFLAKE);
		} else {
			return asList(DEFAULT_OBFS4, VANILLA);
		}
	}

	@Override
	@IoExecutor
	public List<String> getBridges(BridgeType type, String countryCode,
			boolean letsEncrypt) {
		InputStream is = requireNonNull(getClass().getClassLoader()
				.getResourceAsStream(BRIDGE_FILE_NAME));
		Scanner scanner = new Scanner(is);

		List<String> bridges = new ArrayList<>();
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			if ((type == DEFAULT_OBFS4 && line.startsWith("d ")) ||
					(type == NON_DEFAULT_OBFS4 && line.startsWith("n ")) ||
					(type == VANILLA && line.startsWith("v ")) ||
					(type == MEEK && line.startsWith("m "))) {
				bridges.add(line.substring(2));
			} else if (type == SNOWFLAKE && line.startsWith("s ")) {
				String params = getSnowflakeParams(countryCode, letsEncrypt);
				bridges.add(line.substring(2) + " " + params);
			}
		}
		scanner.close();
		return bridges;
	}

	// Package access for testing
	@SuppressWarnings("WeakerAccess")
	String getSnowflakeParams(String countryCode, boolean letsEncrypt) {
		Map<String, String> params = loadSnowflakeParams();
		if (countryCode.isEmpty()) countryCode = DEFAULT_COUNTRY_CODE;
		// If we have parameters for this country code, return them
		String value = params.get(makeKey(countryCode, letsEncrypt));
		if (value != null) return value;
		// Return the default parameters
		value = params.get(makeKey(DEFAULT_COUNTRY_CODE, letsEncrypt));
		return requireNonNull(value);
	}

	private Map<String, String> loadSnowflakeParams() {
		InputStream is = requireNonNull(getClass().getClassLoader()
				.getResourceAsStream(SNOWFLAKE_PARAMS_FILE_NAME));
		Scanner scanner = new Scanner(is);
		Map<String, String> params = new TreeMap<>();
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			if (line.length() < 5) continue;
			String key = line.substring(0, 4); // Country code, space, digit
			String value = line.substring(5);
			params.put(key, value);
		}
		scanner.close();
		return params;
	}

	private String makeKey(String countryCode, boolean letsEncrypt) {
		return countryCode + " " + (letsEncrypt ? "1" : "0");
	}
}
