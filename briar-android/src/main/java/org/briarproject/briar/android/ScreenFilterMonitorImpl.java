package org.briarproject.briar.android;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.support.annotation.UiThread;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.api.android.ScreenFilterMonitor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.Manifest.permission.SYSTEM_ALERT_WINDOW;
import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.content.pm.PackageManager.GET_SIGNATURES;
import static java.util.logging.Level.WARNING;

@NotNullByDefault
class ScreenFilterMonitorImpl implements ScreenFilterMonitor {

	private static final Logger LOG =
			Logger.getLogger(ScreenFilterMonitorImpl.class.getName());

	/*
 	 * Ignore Play Services if it uses this package name and public key - it's
	 * effectively a system app, but not flagged as such on older systems
	 */
	private static final String PLAY_SERVICES_PACKAGE =
			"com.google.android.gms";
	private static final String PLAY_SERVICES_PUBLIC_KEY =
			"30820120300D06092A864886F70D01010105000382010D0030820108" +
					"0282010100AB562E00D83BA208AE0A966F124E29DA11F2AB56D08F58" +
					"E2CCA91303E9B754D372F640A71B1DCB130967624E4656A7776A9219" +
					"3DB2E5BFB724A91E77188B0E6A47A43B33D9609B77183145CCDF7B2E" +
					"586674C9E1565B1F4C6A5955BFF251A63DABF9C55C27222252E875E4" +
					"F8154A645F897168C0B1BFC612EABF785769BB34AA7984DC7E2EA276" +
					"4CAE8307D8C17154D7EE5F64A51A44A602C249054157DC02CD5F5C0E" +
					"55FBEF8519FBE327F0B1511692C5A06F19D18385F5C4DBC2D6B93F68" +
					"CC2979C70E18AB93866B3BD5DB8999552A0E3B4C99DF58FB918BEDC1" +
					"82BA35E003C1B4B10DD244A8EE24FFFD333872AB5221985EDAB0FC0D" +
					"0B145B6AA192858E79020103";

	private final PackageManager pm;

	@Inject
	ScreenFilterMonitorImpl(Application app) {
		pm = app.getPackageManager();
	}

	@Override
	@UiThread
	public Set<String> getApps() {
		Set<String> screenFilterApps = new TreeSet<>();
		List<PackageInfo> packageInfos =
				pm.getInstalledPackages(GET_PERMISSIONS);
		for (PackageInfo packageInfo : packageInfos) {
			if (isOverlayApp(packageInfo)) {
				String name = pkgToString(packageInfo);
				if (name != null) {
					screenFilterApps.add(name);
				}
			}
		}
		return screenFilterApps;
	}

	// Fetches the application name for a given package.
	@Nullable
	private String pkgToString(PackageInfo pkgInfo) {
		CharSequence seq = pm.getApplicationLabel(pkgInfo.applicationInfo);
		if (seq != null) {
			return seq.toString();
		}
		return null;
	}

	// Checks if an installed package is a user app using the permission.
	private boolean isOverlayApp(PackageInfo packageInfo) {
		int mask = FLAG_SYSTEM | FLAG_UPDATED_SYSTEM_APP;
		// Ignore system apps
		if ((packageInfo.applicationInfo.flags & mask) != 0) {
			return false;
		}
		// Ignore Play Services, it's effectively a system app
		if (isPlayServices(packageInfo.packageName)) {
			return false;
		}
		// Get permissions
		String[] requestedPermissions = packageInfo.requestedPermissions;
		if (requestedPermissions != null) {
			for (String requestedPermission : requestedPermissions) {
				if (requestedPermission.equals(SYSTEM_ALERT_WINDOW)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isPlayServices(String pkg) {
		if (!PLAY_SERVICES_PACKAGE.equals(pkg)) return false;
		try {
			PackageInfo sigs = pm.getPackageInfo(pkg, GET_SIGNATURES);
			// The genuine Play Services app should have a single signature
			Signature[] signatures = sigs.signatures;
			if (signatures == null || signatures.length != 1) return false;
			// Extract the public key from the signature
			CertificateFactory certFactory =
					CertificateFactory.getInstance("X509");
			byte[] signatureBytes = signatures[0].toByteArray();
			InputStream in = new ByteArrayInputStream(signatureBytes);
			X509Certificate cert =
					(X509Certificate) certFactory.generateCertificate(in);
			byte[] publicKeyBytes = cert.getPublicKey().getEncoded();
			String publicKey = StringUtils.toHexString(publicKeyBytes);
			return PLAY_SERVICES_PUBLIC_KEY.equals(publicKey);
		} catch (NameNotFoundException | CertificateException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			return false;
		}
	}
}
