package org.briarproject.bramble.plugin.tor.wrapper;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.Build;

import org.briarproject.android.dontkillmelib.wakelock.AndroidWakeLock;
import org.briarproject.android.dontkillmelib.wakelock.AndroidWakeLockManager;
import org.briarproject.nullsafety.NotNullByDefault;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static android.os.Build.VERSION.SDK_INT;
import static java.util.Arrays.asList;
import static java.util.logging.Level.INFO;
import static java.util.logging.Logger.getLogger;

/**
 * A Tor wrapper for the Android operating system.
 */
@NotNullByDefault
public class AndroidTorWrapper extends AbstractTorWrapper {

	private static final List<String> LIBRARY_ARCHITECTURES =
			asList("armeabi-v7a", "arm64-v8a", "x86", "x86_64");

	private static final String TOR_LIB_NAME = "libtor.so";
	private static final String OBFS4_LIB_NAME = "libobfs4proxy.so";
	private static final String SNOWFLAKE_LIB_NAME = "libsnowflake.so";

	private static final Logger LOG =
			getLogger(AndroidTorWrapper.class.getName());

	private final Application app;
	private final AndroidWakeLock wakeLock;
	private final File torLib, obfs4Lib, snowflakeLib;

	/**
	 * @param app The application instance.
	 * @param wakeLockManager The interface for managing a shared wake lock.
	 * @param ioExecutor The wrapper will use this executor to run IO tasks,
	 * some of which may run for the lifetime of the wrapper, so the executor
	 * should have an unlimited thread pool.
	 * @param eventExecutor The wrapper will use this executor to call
	 * {@link StateObserver#observeState(TorState)}. To ensure that state
	 * changes are observed in the order they occur, this executor should have
	 * a single thread (eg the app's main thread).
	 * @param architecture The processor architecture of the Tor and pluggable
	 * transport binaries.
	 * @param torDirectory The directory where the Tor process should keep its
	 * state.
	 * @param torSocksPort The port number to use for Tor's SOCKS port.
	 * @param torControlPort The port number to use for Tor's control port.
	 */
	public AndroidTorWrapper(Application app,
			AndroidWakeLockManager wakeLockManager,
			Executor ioExecutor,
			Executor eventExecutor,
			String architecture,
			File torDirectory,
			int torSocksPort,
			int torControlPort) {
		super(ioExecutor, eventExecutor, architecture, torDirectory,
				torSocksPort, torControlPort);
		this.app = app;
		wakeLock = wakeLockManager.createWakeLock("TorPlugin");
		String nativeLibDir = app.getApplicationInfo().nativeLibraryDir;
		torLib = new File(nativeLibDir, TOR_LIB_NAME);
		obfs4Lib = new File(nativeLibDir, OBFS4_LIB_NAME);
		snowflakeLib = new File(nativeLibDir, SNOWFLAKE_LIB_NAME);
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
	public InputStream getResourceInputStream(String name, String extension) {
		Resources res = app.getResources();
		// Extension is ignored on Android, resources are retrieved without it
		int resId = res.getIdentifier(name, "raw", app.getPackageName());
		return res.openRawResource(resId);
	}

	@Override
	public void enableNetwork(boolean enable) throws IOException {
		if (enable) wakeLock.acquire();
		try {
			super.enableNetwork(enable);
		} finally {
			if (!enable) wakeLock.release();
		}
	}

	@Override
	public void stop() throws IOException {
		try {
			super.stop();
		} finally {
			wakeLock.release();
		}
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
	protected File getSnowflakeExecutableFile() {
		return snowflakeLib.exists() ? snowflakeLib
				: super.getSnowflakeExecutableFile();
	}

	@Override
	protected void installTorExecutable() throws IOException {
		installExecutable(super.getTorExecutableFile(), torLib, TOR_LIB_NAME);
	}

	@Override
	protected void installObfs4Executable() throws IOException {
		installExecutable(super.getObfs4ExecutableFile(), obfs4Lib,
				OBFS4_LIB_NAME);
	}

	@Override
	protected void installSnowflakeExecutable() throws IOException {
		installExecutable(super.getSnowflakeExecutableFile(), snowflakeLib,
				SNOWFLAKE_LIB_NAME);
	}

	private void installExecutable(File extracted, File lib, String libName)
			throws IOException {
		if (lib.exists()) {
			// If an older version left behind a binary, delete it
			if (extracted.exists()) {
				if (extracted.delete()) LOG.info("Deleted old binary");
				else LOG.info("Failed to delete old binary");
			}
		} else if (SDK_INT < 29) {
			// The binary wasn't extracted at install time. Try to extract it
			extractLibraryFromApk(libName, extracted);
		} else {
			// No point extracting the binary, we won't be allowed to execute it
			throw new FileNotFoundException(lib.getAbsolutePath());
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
			@SuppressWarnings("IOStreamConstructor")
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
		for (String abi : getSupportedArchitectures()) {
			if (LIBRARY_ARCHITECTURES.contains(abi)) {
				architectures.add("lib/" + abi + "/" + libName);
			}
		}
		return architectures;
	}

	private Collection<String> getSupportedArchitectures() {
		List<String> abis = new ArrayList<>();
		if (SDK_INT >= 21) {
			abis.addAll(asList(Build.SUPPORTED_ABIS));
		} else {
			abis.add(Build.CPU_ABI);
			if (Build.CPU_ABI2 != null) abis.add(Build.CPU_ABI2);
		}
		return abis;
	}
}
