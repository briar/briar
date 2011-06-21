package net.sf.briar.invitation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import net.sf.briar.api.invitation.InvitationCallback;
import net.sf.briar.api.invitation.InvitationParameters;
import net.sf.briar.util.FileUtils;

class InvitationWorker implements Runnable {

	private final InvitationCallback callback;
	private final InvitationParameters parameters;

	InvitationWorker(final InvitationCallback callback,
			InvitationParameters parameters) {
		this.callback = callback;
		this.parameters = parameters;
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
		if(!dir.canWrite()) {
			callback.notAllowed(dir);
			return;
		}
		List<File> files = new ArrayList<File>();
		try {
			if(callback.isCancelled()) return;
			File invitationDat = createInvitationDat(dir);
			files.add(invitationDat);
			if(callback.isCancelled()) return;
			if(parameters.shouldCreateExe()) {
				File briarExe = createBriarExe(dir);
				files.add(briarExe);
			}
			if(callback.isCancelled()) return;
			if(parameters.shouldCreateJar()) {
				File briarJar = createBriarJar(dir);
				files.add(briarJar);
			}
		} catch(IOException e) {
			callback.error(e.getMessage());
			return;
		}
		if(callback.isCancelled()) return;
		callback.created(files);
	}

	private File createInvitationDat(File dir) throws IOException {
		char[] password = parameters.getPassword();
		assert password != null;
		File invitationDat = new File(dir, "invitation.dat");
		callback.encryptingFile(invitationDat);
		// FIXME: Create a real invitation
		try {
			Thread.sleep(2000);
		} catch(InterruptedException ignored) {
		}
		Arrays.fill(password, (char) 0);
		FileOutputStream out = new FileOutputStream(invitationDat);
		byte[] buf = new byte[1024];
		new Random().nextBytes(buf);
		out.write(buf, 0, buf.length);
		out.flush();
		out.close();
		return invitationDat;
	}

	private File createBriarExe(File dir) throws IOException {
		File f = new File(dir, "briar.exe");
		copyInstaller(f);
		return f;
	}

	private File createBriarJar(File dir) throws IOException {
		File f = new File(dir, "briar.jar");
		copyInstaller(f);
		return f;
	}

	private void copyInstaller(File dest) throws IOException {
		File root = FileUtils.getBriarDirectory();
		File src = new File(root, "Data/setup.dat");
		if(!src.exists() || !src.isFile())
			throw new IOException("File not found: " + src.getPath());
		callback.copyingFile(dest);
		FileUtils.copy(src, dest);
	}
}