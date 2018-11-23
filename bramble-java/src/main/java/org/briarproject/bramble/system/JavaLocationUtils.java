package org.briarproject.bramble.system;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.LocationUtils;

import java.util.Locale;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Logger.getLogger;

@NotNullByDefault
class JavaLocationUtils implements LocationUtils {

	private static final Logger LOG =
			getLogger(JavaLocationUtils.class.getName());

	@Inject
	JavaLocationUtils() {
	}

	@Override
	public String getCurrentCountry() {
		LOG.info("Using user-defined locale");
		return Locale.getDefault().getCountry();
	}

}
