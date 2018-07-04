package org.briarproject.bramble.plugin.tor;

import android.content.Context;
import android.content.res.Resources;

import org.briarproject.bramble.api.lifecycle.IoExecutor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import javax.annotation.Nullable;

public class BridgeProviderImpl implements BridgeProvider {

	private final static String BRIDGE_FILE_NAME = "bridges";

	@Nullable
	private volatile List<String> bridges = null;

	@Override
	@IoExecutor
	public List<String> getBridges(Context context) {
		if (this.bridges != null) return this.bridges;

		Resources res = context.getResources();
		int resId = res.getIdentifier(BRIDGE_FILE_NAME, "raw",
				context.getPackageName());
		InputStream is = context.getResources().openRawResource(resId);
		Scanner scanner = new Scanner(is);

		List<String> bridges = new ArrayList<>();
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			if (!line.startsWith("#")) bridges.add(line);
		}
		this.bridges = bridges;
		return bridges;
	}

}
