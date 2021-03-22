package org.briarproject.briar.socialbackup;

import org.briarproject.briar.android.socialbackup.DarkCrystalImpl;
import org.briarproject.briar.api.socialbackup.DarkCrystal;

import dagger.Module;
import dagger.Provides;

@Module
public class AndroidDarkCrystalModule {

	@Provides
	DarkCrystal darkCrystal(DarkCrystalImpl darkCrystal) {
		return darkCrystal;
	}
}
