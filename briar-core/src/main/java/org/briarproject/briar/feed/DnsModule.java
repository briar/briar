package org.briarproject.briar.feed;

import dagger.Module;
import dagger.Provides;
import okhttp3.Dns;

/**
 * This is a dedicated module, so it can be replaced for testing.
 */
@Module
public class DnsModule {

	@Provides
	Dns provideDns(NoDns noDns) {
		return noDns;
	}

}
