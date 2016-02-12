package org.briarproject.plugins;

import org.briarproject.api.plugins.Backoff;
import org.briarproject.api.plugins.BackoffFactory;

class BackoffFactoryImpl implements BackoffFactory {

	@Override
	public Backoff createBackoff(int minInterval, int maxInterval,
			double base) {
		return new BackoffImpl(minInterval, maxInterval, base);
	}
}
