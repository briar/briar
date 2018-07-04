package org.briarproject.bramble.plugin.tor;

import android.content.Context;

import org.briarproject.bramble.api.lifecycle.IoExecutor;

import java.util.List;

public interface BridgeProvider {

	@IoExecutor
	List<String> getBridges(Context context);

}
