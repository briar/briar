package org.briarproject.briar.socialbackup;

import org.briarproject.briar.api.socialbackup.DarkCrystal;

import dagger.Module;
import dagger.Provides;

@Module
public class DefaultDarkCrystalModule {

	@Provides
	DarkCrystal darkCrystal(DarkCrystalStub darkCrystal) {
		return darkCrystal;
	}
}
