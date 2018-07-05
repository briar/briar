package org.briarproject.bramble.plugin.tor;

import android.content.Context;
import android.content.res.Resources;

import org.briarproject.bramble.api.lifecycle.IoExecutor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;

public class CircumventionProviderImpl implements CircumventionProvider {

	private final static String BRIDGE_FILE_NAME = "bridges";

	private final Context ctx;
	@Nullable
	private volatile List<String> bridges = null;

	@Inject
	public CircumventionProviderImpl(Context ctx) {
		this.ctx = ctx;
	}

	private static final Set<String> BLOCKED_IN_COUNTRIES =
			new HashSet<>(Arrays.asList(BLOCKED));
	private static final Set<String> BRIDGES_WORK_IN_COUNTRIES =
			new HashSet<>(Arrays.asList(BRIDGES));

	@Override
	public boolean isTorProbablyBlocked(String countryCode) {
		return BLOCKED_IN_COUNTRIES.contains(countryCode);
	}

	@Override
	public boolean doBridgesWork(String countryCode) {
		return BRIDGES_WORK_IN_COUNTRIES.contains(countryCode);
	}

	@Override
	@IoExecutor
	public List<String> getBridges() {
		if (this.bridges != null) return this.bridges;

		Resources res = ctx.getResources();
		int resId = res.getIdentifier(BRIDGE_FILE_NAME, "raw",
				ctx.getPackageName());
		InputStream is = ctx.getResources().openRawResource(resId);
		Scanner scanner = new Scanner(is);

		List<String> bridges = new ArrayList<>();
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			if (!line.startsWith("#")) bridges.add(line);
		}
		scanner.close();
		this.bridges = bridges;
		return bridges;
	}

}
