package net.sf.briar.invitation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.invitation.InvitationCallback;
import net.sf.briar.api.invitation.InvitationParameters;
import net.sf.briar.api.protocol.Transport.TransportDetails;
import net.sf.briar.api.protocol.Transport.TransportDetails.TransportDetail;
import net.sf.briar.util.FileUtils;

class InvitationWorker implements Runnable {

	private final InvitationCallback callback;
	private final InvitationParameters parameters;
	private final DatabaseComponent databaseComponent;

	InvitationWorker(final InvitationCallback callback,
			InvitationParameters parameters,
			DatabaseComponent databaseComponent) {
		this.callback = callback;
		this.parameters = parameters;
		this.databaseComponent = databaseComponent;
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
			files.add(createInvitationDat(dir));
			if(callback.isCancelled()) return;
			if(parameters.shouldCreateExe()) files.add(createBriarExe(dir));
			if(callback.isCancelled()) return;
			if(parameters.shouldCreateJar()) files.add(createBriarJar(dir));
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
		Map<String, String> transports;
		try {
			transports = databaseComponent.getTransports();
		} catch(DbException e) {
			throw new IOException(e);
		}
		TransportDetails.Builder b = TransportDetails.newBuilder();
		for(Entry<String, String> e : transports.entrySet()) {
			TransportDetail.Builder b1 = TransportDetail.newBuilder();
			b1.setKey(e.getKey());
			b1.setValue(e.getValue());
			b.addDetails(b1.build());
		}
		TransportDetails t = b.build();
		FileOutputStream out = new FileOutputStream(invitationDat);
		t.writeTo(out);
		out.flush();
		out.close();
		Arrays.fill(password, (char) 0);
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
		File src = parameters.getSetupDat();
		if(!src.exists() || !src.isFile())
			throw new IOException("File not found: " + src.getPath());
		callback.copyingFile(dest);
		FileUtils.copy(src, dest);
	}
}