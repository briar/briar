package org.briarproject.briar.android;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.support.annotation.UiThread;
import android.support.v7.preference.PreferenceManager;

import org.briarproject.bramble.api.lifecycle.Service;
import org.briarproject.bramble.api.lifecycle.ServiceException;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.api.android.ScreenFilterMonitor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.Manifest.permission.SYSTEM_ALERT_WINDOW;
import static android.content.Intent.ACTION_PACKAGE_ADDED;
import static android.content.Intent.EXTRA_REPLACING;
import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.content.pm.PackageManager.GET_SIGNATURES;
import static java.util.logging.Level.WARNING;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ScreenFilterMonitorImpl extends BroadcastReceiver
		implements Service, ScreenFilterMonitor {

	private static final Logger LOG =
			Logger.getLogger(ScreenFilterMonitorImpl.class.getName());
	private static final String PREF_SCREEN_FILTER_APPS =
			"shownScreenFilterApps";

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

	private final Context appContext;
	private final AndroidExecutor androidExecutor;
	private final PackageManager pm;
	private final SharedPreferences prefs;
	private final AtomicBoolean used = new AtomicBoolean(false);

	// The following must only be accessed on the UI thread
	private final Set<String> apps = new HashSet<>();
	private final Set<String> shownApps;
	private boolean serviceStarted = false;

	@Inject
	ScreenFilterMonitorImpl(AndroidExecutor executor, Application app) {
		this.androidExecutor = executor;
		this.appContext = app;
		pm = appContext.getPackageManager();
		prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
		shownApps = getShownScreenFilterApps();
	}

	@Override
	public void startService() throws ServiceException {
		if (used.getAndSet(true)) throw new IllegalStateException();
		Future<Void> f = androidExecutor.runOnUiThread(new Callable<Void>() {
			@Override
			public Void call() {
				IntentFilter intentFilter = new IntentFilter();
				intentFilter.addAction(ACTION_PACKAGE_ADDED);
				intentFilter.addDataScheme("package");
				appContext.registerReceiver(ScreenFilterMonitorImpl.this,
						intentFilter);
				apps.addAll(getInstalledScreenFilterApps());
				serviceStarted = true;
				return null;
			}
		});
		try {
			f.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new ServiceException(e);
		}
	}

	@Override
	public void stopService() throws ServiceException {
		Future<Void> f = androidExecutor.runOnUiThread(new Callable<Void>() {
			@Override
			public Void call() {
				serviceStarted = false;
				appContext.unregisterReceiver(ScreenFilterMonitorImpl.this);
				return null;
			}
		});
		try {
			f.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new ServiceException(e);
		}
	}

	private Set<String> getShownScreenFilterApps() {
		// Result must not be modified
		Set<String> s = prefs.getStringSet(PREF_SCREEN_FILTER_APPS, null);
		HashSet<String> result = new HashSet<>();
		if (s != null) {
			result.addAll(s);
		}
		return result;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		if (!intent.getBooleanExtra(EXTRA_REPLACING, false)) {
			final String packageName =
					intent.getData().getEncodedSchemeSpecificPart();
			androidExecutor.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					String pkg = isOverlayApp(packageName);
					if (pkg == null) {
						return;
					}
					apps.add(pkg);
				}
			});
		}
	}

	@Override
	@UiThread
	public Set<String> getApps() {
		if (!serviceStarted) {
			apps.addAll(getInstalledScreenFilterApps());
		}
		TreeSet<String> buf = new TreeSet<>();
		if (apps.isEmpty()) {
			return buf;
		}
		buf.addAll(apps);
		buf.removeAll(shownApps);
		return buf;
	}

	@Override
	@UiThread
	public void storeAppsAsShown(Collection<String> s, boolean persistent) {
		HashSet<String> buf = new HashSet<>(s);
		shownApps.addAll(buf);
		if (persistent && !s.isEmpty()) {
			buf.addAll(getShownScreenFilterApps());
			prefs.edit()
					.putStringSet(PREF_SCREEN_FILTER_APPS, buf)
					.apply();
		}
	}

	private Set<String> getInstalledScreenFilterApps() {
		HashSet<String> screenFilterApps = new HashSet<>();
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

	// Checks if a package uses the SYSTEM_ALERT_WINDOW permission and if so
	// returns the app name.
	@Nullable
	private String isOverlayApp(String pkg) {
		try {
			PackageInfo pkgInfo = pm.getPackageInfo(pkg, GET_PERMISSIONS);
			if (isOverlayApp(pkgInfo)) {
				return pkgToString(pkgInfo);
			}
		} catch (NameNotFoundException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
		return null;
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
