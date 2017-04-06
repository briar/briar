package org.briarproject.briar.android;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v7.preference.PreferenceManager;

import org.briarproject.bramble.api.lifecycle.Service;
import org.briarproject.bramble.api.lifecycle.ServiceException;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.api.android.ScreenFilterMonitor;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.Manifest.permission.SYSTEM_ALERT_WINDOW;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ScreenFilterMonitorImpl extends BroadcastReceiver
		implements Service,
		ScreenFilterMonitor {

	private static final Logger LOG =
			Logger.getLogger(ScreenFilterMonitorImpl.class.getName());
	private static final String PREF_SCREEN_FILTER_APPS =
			"shownScreenFilterApps";
	private final Context appContext;
	private final AndroidExecutor androidExecutor;
	private final LinkedList<String> appNames = new LinkedList<>();
	private final PackageManager pm;
	private final SharedPreferences prefs;
	private final AtomicBoolean used = new AtomicBoolean(false);
	private final Set<String> apps = new HashSet<>();
	private final Set<String> shownApps;
	// Used solely for the UiThread
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
				intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
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
		// res must not be modified
		Set<String> s =
				prefs.getStringSet(PREF_SCREEN_FILTER_APPS, null);
		HashSet<String> result = new HashSet<>();
		if (s != null) {
			result.addAll(s);
		}
		return result;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
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
		HashSet<String> buf = new HashSet(s);
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
				pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
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

	// Checks if pkg uses the SYSTEM_ALERT_WINDOW permission and if so
	// returns the app name.
	@Nullable
	private String isOverlayApp(String pkg) {
		try {
			PackageInfo pkgInfo =
					pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);
			if (isOverlayApp(pkgInfo)) {
				return pkgToString(pkgInfo);
			}
		} catch (PackageManager.NameNotFoundException ignored) {
			if (LOG.isLoggable(Level.WARNING)) {
				LOG.warning("Package name not found: " + pkg);
			}
		}
		return null;
	}

	// Fetch the application name for a given package.
	@Nullable
	private String pkgToString(PackageInfo pkgInfo) {
		CharSequence seq = pm.getApplicationLabel(pkgInfo.applicationInfo);
		if (seq != null) {
			return seq.toString();
		}
		return null;
	}

	// Checks if an installed pkg is a user app using the permission.
	private boolean isOverlayApp(PackageInfo packageInfo) {
		int mask = ApplicationInfo.FLAG_SYSTEM |
				ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
		// Ignore system apps
		if ((packageInfo.applicationInfo.flags & mask) != 0) {
			return false;
		}
		//Get Permissions
		String[] requestedPermissions =
				packageInfo.requestedPermissions;
		if (requestedPermissions != null) {
			for (String requestedPermission : requestedPermissions) {
				if (requestedPermission
						.equals(SYSTEM_ALERT_WINDOW)) {
					return true;
				}
			}
		}
		return false;
	}
}
