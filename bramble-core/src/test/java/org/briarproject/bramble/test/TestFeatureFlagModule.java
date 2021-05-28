package org.briarproject.bramble.test;

import org.briarproject.bramble.api.FeatureFlags;

import dagger.Module;
import dagger.Provides;

@Module
public class TestFeatureFlagModule {
	@Provides
	FeatureFlags provideFeatureFlags() {
		return new FeatureFlags() {
			@Override
			public boolean shouldEnableImageAttachments() {
				return true;
			}

			@Override
			public boolean shouldEnableProfilePictures() {
				return true;
			}

			@Override
			public boolean shouldEnableDisappearingMessages() {
				return true;
			}

			@Override
			public boolean shouldEnableConnectViaBluetooth() {
				return true;
			}

			@Override
			public boolean shouldEnableTransferData() {
				return true;
			}

			@Override
			public boolean shouldEnableShareAppViaOfflineHotspot() {
				return true;
			}
		};
	}
}
