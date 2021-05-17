package org.briarproject.briar.android.hotspot;

import android.graphics.Bitmap;

import androidx.annotation.Nullable;

abstract class HotspotState {

	static class StartingHotspot extends HotspotState {
	}

	static class NetworkConfig {
		final String ssid, password;
		@Nullable
		final Bitmap qrCode;

		NetworkConfig(String ssid, String password, @Nullable Bitmap qrCode) {
			this.ssid = ssid;
			this.password = password;
			this.qrCode = qrCode;
		}
	}

	static class WebsiteConfig {
		final String url;
		@Nullable
		final Bitmap qrCode;

		WebsiteConfig(String url, @Nullable Bitmap qrCode) {
			this.url = url;
			this.qrCode = qrCode;
		}
	}

	static class HotspotStarted extends HotspotState {
		private final NetworkConfig networkConfig;
		private final WebsiteConfig websiteConfig;

		HotspotStarted(NetworkConfig networkConfig,
				WebsiteConfig websiteConfig) {
			this.networkConfig = networkConfig;
			this.websiteConfig = websiteConfig;
		}

		NetworkConfig getNetworkConfig() {
			return networkConfig;
		}

		WebsiteConfig getWebsiteConfig() {
			return websiteConfig;
		}
	}

	static class HotspotError extends HotspotState {
		private final String error;

		HotspotError(String error) {
			this.error = error;
		}

		String getError() {
			return error;
		}
	}

}
