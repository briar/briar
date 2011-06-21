package net.sf.briar.setup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.security.CodeSource;

import net.sf.briar.api.i18n.I18n;
import net.sf.briar.api.setup.SetupCallback;
import net.sf.briar.api.setup.SetupParameters;
import net.sf.briar.util.FileUtils;
import net.sf.briar.util.OsUtils;
import net.sf.briar.util.ZipUtils;

class SetupWorker implements Runnable {

	private static final String MAIN_CLASS =
		"net.sf.briar.ui.invitation.InvitationMain";
	private static final int EXE_HEADER_SIZE = 62976;

	private final SetupCallback callback;
	private final SetupParameters parameters;
	private final I18n i18n;
	private final ZipUtils.Callback unzipCallback;

	SetupWorker(final SetupCallback callback, SetupParameters parameters,
			I18n i18n) {
		this.parameters = parameters;
		this.callback = callback;
		this.i18n = i18n;
		unzipCallback = new ZipUtils.Callback() {
			public void processingFile(File f) {
				callback.extractingFile(f);
			}
		};
	}

	public void run() {
		File dir = parameters.getChosenLocation();
		assert dir != null;
		if(!dir.exists()) {
			callback.notFound(dir);
			return;
		}
		if(!dir.isDirectory()) {
			callback.notDirectory(dir);
			return;
		}
		String[] list = dir.list();
		if(list == null || !dir.canWrite()) {
			callback.notAllowed(dir);
			return;
		}
		if(list.length != 0) {
			dir = new File(dir, "Briar");
			if(!dir.exists() && !dir.mkdir()) {
				callback.notAllowed(dir);
				return;
			}
		}
		File data = new File(dir, "Data");
		if(!data.exists() && !data.mkdir()) {
			callback.notAllowed(data);
			return;
		}
		try {
			if(callback.isCancelled()) return;
			File jar = getJar();
			if(callback.isCancelled()) return;
			copyInstaller(jar, data);
			if(callback.isCancelled()) return;
			extractFiles(jar, data, "^jre/.*|.*\\.jar$|.*\\.ttf$");
			if(callback.isCancelled()) return;
			createLaunchers(dir);
			if(callback.isCancelled()) return;
			i18n.saveLocale(data);
			if(callback.isCancelled()) return;
			jar.deleteOnExit();
		} catch(IOException e) {
			callback.error(e.getMessage());
			return;
		}
		if(callback.isCancelled()) return;
		callback.installed(dir);
	}

	private File getJar() throws IOException {
		CodeSource c = FileUtils.class.getProtectionDomain().getCodeSource();
		File jar = new File(c.getLocation().getPath());
		assert jar.exists();
		if(!jar.isFile()) throw new IOException("Not running from a jar");
		return jar;
	}

	private void copyInstaller(File jar, File dir) throws IOException {
		File dest = new File(dir, "setup.dat");
		callback.copyingFile(dest);
		FileUtils.copy(jar, dest);
	}

	private void extractFiles(File jar, File dir, String regex)
	throws IOException {
		FileInputStream in = new FileInputStream(jar);
		in.skip(EXE_HEADER_SIZE);
		ZipUtils.unzipStream(in, dir, regex, unzipCallback);
	}

	private void createLaunchers(File dir) throws IOException {
		createWindowsLauncher(dir);
		File mac = createMacLauncher(dir);
		File lin = createLinuxLauncher(dir);
		if(!OsUtils.isWindows()) {
			String[] chmod = { "chmod", "u+x", mac.getName(), lin.getName() };
			ProcessBuilder p = new ProcessBuilder(chmod);
			p.directory(dir);
			p.start();
		}
	}

	private File createWindowsLauncher(File dir) throws IOException {
		File launcher = new File(dir, "run-windows.vbs");
		PrintStream out = new PrintStream(new FileOutputStream(launcher));
		out.print("Set Shell = CreateObject(\"WScript.Shell\")\r\n");
		out.print("Shell.Run \"Data\\jre\\bin\\javaw -ea -cp Data\\* "
				+ MAIN_CLASS + "\", 0\r\n");
		out.print("Set Shell = Nothing\r\n");
		out.flush();
		out.close();
		return launcher;
	}

	// FIXME: If this pops up a terminal window, the Mac launcher may need
	// to be a jar
	private File createMacLauncher(File dir) throws IOException {
		File launcher = new File(dir, "run-mac.command");
		PrintStream out = new PrintStream(new FileOutputStream(launcher));
		out.print("#!/bin/sh\n");
		out.print("cd \"$(dirname \"$0\")\"\n");
		out.print("java -ea -cp 'Data/*' " + MAIN_CLASS + "\n");
		out.flush();
		out.close();
		return launcher;
	}

	private File createLinuxLauncher(File dir) throws IOException {
		File launcher = new File(dir, "run-linux.sh");
		PrintStream out = new PrintStream(new FileOutputStream(launcher));
		out.print("#!/bin/sh\n");
		out.print("cd \"$(dirname \"$0\")\"\n");
		out.print("java -ea -cp 'Data/*' " + MAIN_CLASS + "\n");
		out.flush();
		out.close();
		return launcher;
	}
}
