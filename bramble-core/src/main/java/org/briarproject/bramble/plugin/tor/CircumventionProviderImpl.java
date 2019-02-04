package org.briarproject.bramble.plugin.tor;

import org.briarproject.bramble.api.lifecycle.IoExecutor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static java.util.Arrays.asList;

class CircumventionProviderImpl implements CircumventionProvider {

	private final static String BRIDGE_FILE_NAME = "bridges";

	private static final Set<String> BLOCKED_IN_COUNTRIES =
			new HashSet<>(asList(BLOCKED));
	private static final Set<String> BRIDGES_WORK_IN_COUNTRIES =
			new HashSet<>(asList(BRIDGES));
	private static final Set<String> BRIDGES_NEED_MEEK =
			new HashSet<>(asList(NEEDS_MEEK));

	@Nullable
	private volatile List<String> bridges = null;

	@Inject
	CircumventionProviderImpl() {
	}

	@Override
	public boolean isTorProbablyBlocked(String countryCode) {
		return BLOCKED_IN_COUNTRIES.contains(countryCode);
	}

	@Override
	public boolean doBridgesWork(String countryCode) {
		return BRIDGES_WORK_IN_COUNTRIES.contains(countryCode);
	}

	@Override
	public boolean needsMeek(String countryCode) {
		return BRIDGES_NEED_MEEK.contains(countryCode);
	}

	@Override
	@IoExecutor
	public List<String> getBridges(boolean useMeek) {
		List<String> bridges = this.bridges;
		if (bridges != null) return new ArrayList<>(bridges);

		InputStream is = getClass().getClassLoader()
				.getResourceAsStream(BRIDGE_FILE_NAME);
		Scanner scanner = new Scanner(is);

		bridges = new ArrayList<>();
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			boolean isMeekBridge = line.startsWith("Bridge meek");
			if (useMeek && !isMeekBridge || !useMeek && isMeekBridge) continue;
			if (!line.startsWith("#")) bridges.add(line);
		}
		scanner.close();
		this.bridges = bridges;
		return bridges;
	}

}
