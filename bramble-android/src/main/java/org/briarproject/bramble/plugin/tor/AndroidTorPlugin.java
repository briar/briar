package org.briarproject.bramble.plugin.tor;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import org.briarproject.bramble.api.battery.BatteryManager;
import org.briarproject.bramble.api.network.NetworkManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.system.AndroidWakeLock;
import org.briarproject.bramble.api.system.AndroidWakeLockManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.api.system.ResourceProvider;
import org.briarproject.bramble.util.AndroidUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.net.SocketFactory;

import static android.os.Build.VERSION.SDK_INT;
import static java.util.Arrays.asList;
import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class AndroidTorPlugin extends TorPlugin {

	private static final List<String> LIBRARY_ARCHITECTURES =
			asList("armeabi-v7a", "arm64-v8a", "x86", "x86_64");

	private static final String TOR_LIB_NAME = "libtor.so";
	private static final String OBFS4_LIB_NAME = "libobfs4proxy.so";

	private static final Logger LOG =
			getLogger(AndroidTorPlugin.class.getName());

	private final Application app;
	private final AndroidWakeLock wakeLock;
	private final File torLib, obfs4Lib;

	AndroidTorPlugin(Executor ioExecutor,
			Executor wakefulIoExecutor,
			Application app,
			NetworkManager networkManager,
			LocationUtils locationUtils,
			SocketFactory torSocketFactory,
			Clock clock,
			ResourceProvider resourceProvider,
			CircumventionProvider circumventionProvider,
			BatteryManager batteryManager,
			AndroidWakeLockManager wakeLockManager,
			Backoff backoff,
			TorRendezvousCrypto torRendezvousCrypto,
			PluginCallback callback,
			String architecture,
			long maxLatency,
			int maxIdleTime,
			File torDirectory,
			int torSocksPort,
			int torControlPort) {
		super(ioExecutor, wakefulIoExecutor, networkManager, locationUtils,
				torSocketFactory, clock, resourceProvider,
				circumventionProvider, batteryManager, backoff,
				torRendezvousCrypto, callback, architecture, maxLatency,
				maxIdleTime, torDirectory, torSocksPort, torControlPort);
		this.app = app;
		wakeLock = wakeLockManager.createWakeLock("TorPlugin");
		String nativeLibDir = app.getApplicationInfo().nativeLibraryDir;
		torLib = new File(nativeLibDir, TOR_LIB_NAME);
		obfs4Lib = new File(nativeLibDir, OBFS4_LIB_NAME);
	}

	@Override
	protected int getProcessId() {
		return android.os.Process.myPid();
	}

	@Override
	protected long getLastUpdateTime() {
		try {
			PackageManager pm = app.getPackageManager();
			PackageInfo pi = pm.getPackageInfo(app.getPackageName(), 0);
			return pi.lastUpdateTime;
		} catch (NameNotFoundException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	protected void enableNetwork(boolean enable) throws IOException {
		if (enable) wakeLock.acquire();
		super.enableNetwork(enable);
		if (!enable) wakeLock.release();
	}

	@Override
	public void stop() {
		super.stop();
		wakeLock.release();
	}

	@Override
	protected File getTorExecutableFile() {
		return torLib.exists() ? torLib : super.getTorExecutableFile();
	}

	@Override
	protected File getObfs4ExecutableFile() {
		return obfs4Lib.exists() ? obfs4Lib : super.getObfs4ExecutableFile();
	}

	@Override
	protected void installTorExecutable() throws IOException {
		File extracted = super.getTorExecutableFile();
		if (torLib.exists()) {
			// If an older version left behind a Tor binary, delete it
			if (extracted.exists()) {
				if (extracted.delete()) LOG.info("Deleted Tor binary");
				else LOG.info("Failed to delete Tor binary");
			}
		} else if (SDK_INT < 29) {
			// The binary wasn't extracted at install time. Try to extract it
			extractLibraryFromApk(TOR_LIB_NAME, extracted);
		} else {
			// No point extracting the binary, we won't be allowed to execute it
			throw new FileNotFoundException(torLib.getAbsolutePath());
		}
	}

	@Override
	protected void installObfs4Executable() throws IOException {
		File extracted = super.getObfs4ExecutableFile();
		if (obfs4Lib.exists()) {
			// If an older version left behind an obfs4 binary, delete it
			if (extracted.exists()) {
				if (extracted.delete()) LOG.info("Deleted obfs4 binary");
				else LOG.info("Failed to delete obfs4 binary");
			}
		} else if (SDK_INT < 29) {
			// The binary wasn't extracted at install time. Try to extract it
			extractLibraryFromApk(OBFS4_LIB_NAME, extracted);
		} else {
			// No point extracting the binary, we won't be allowed to execute it
			throw new FileNotFoundException(obfs4Lib.getAbsolutePath());
		}
	}

	private void extractLibraryFromApk(String libName, File dest)
			throws IOException {
		File sourceDir = new File(app.getApplicationInfo().sourceDir);
		if (sourceDir.isFile()) {
			// Look for other APK files in the same directory, if we're allowed
			File parent = sourceDir.getParentFile();
			if (parent != null) sourceDir = parent;
		}
		List<String> libPaths = getSupportedLibraryPaths(libName);
		for (File apk : findApkFiles(sourceDir)) {
			ZipInputStream zin = new ZipInputStream(new FileInputStream(apk));
			for (ZipEntry e = zin.getNextEntry(); e != null;
					e = zin.getNextEntry()) {
				if (libPaths.contains(e.getName())) {
					if (LOG.isLoggable(INFO)) {
						LOG.info("Extracting " + e.getName()
								+ " from " + apk.getAbsolutePath());
					}
					extract(zin, dest); // Zip input stream will be closed
					return;
				}
			}
			zin.close();
		}
		throw new FileNotFoundException(libName);
	}

	/**
	 * Returns all files with the extension .apk or .APK under the given root.
	 */
	private List<File> findApkFiles(File root) {
		List<File> files = new ArrayList<>();
		findApkFiles(root, files);
		return files;
	}

	private void findApkFiles(File f, List<File> files) {
		if (f.isFile() && f.getName().toLowerCase().endsWith(".apk")) {
			files.add(f);
		} else if (f.isDirectory()) {
			File[] children = f.listFiles();
			if (children != null) {
				for (File child : children) findApkFiles(child, files);
			}
		}
	}

	/**
	 * Returns the paths at which libraries with the given name would be found
	 * inside an APK file, for all architectures supported by the device, in
	 * order of preference.
	 */
	private List<String> getSupportedLibraryPaths(String libName) {
		List<String> architectures = new ArrayList<>();
		for (String abi : AndroidUtils.getSupportedArchitectures()) {
			if (LIBRARY_ARCHITECTURES.contains(abi)) {
				architectures.add("lib/" + abi + "/" + libName);
			}
		}
		return architectures;
	}
}
