package net.sf.briar.ui.invitation;

import java.io.File;
import java.util.List;

import net.sf.briar.api.i18n.I18n;
import net.sf.briar.api.i18n.Stri18ng;
import net.sf.briar.api.invitation.InvitationCallback;
import net.sf.briar.api.invitation.InvitationParameters;
import net.sf.briar.api.invitation.InvitationWorkerFactory;
import net.sf.briar.ui.wizard.Wizard;
import net.sf.briar.ui.wizard.WorkerPanel;
import net.sf.briar.util.StringUtils;

class InvitationWorkerPanel extends WorkerPanel implements InvitationCallback {

	private static final long serialVersionUID = 3668512976295525240L;

	private static final int MAX_LINE_LENGTH = 40;

	private final InvitationWorkerFactory workerFactory;
	private final InvitationParameters parameters;
	private final Stri18ng copying, encrypting, created, giveToContact;
	private final Stri18ng aborted, error, notFound, notDir, notAllowed;

	InvitationWorkerPanel(Wizard wizard, InvitationWorkerFactory workerFactory,
			InvitationParameters parameters, I18n i18n) {
		super(wizard, "InvitationWorker",
				new Stri18ng("INVITATION_PROGRESS_BEGIN", i18n),
				new Stri18ng("CANCELLING", i18n));
		this.workerFactory = workerFactory;
		this.parameters = parameters;
		copying = new Stri18ng("COPYING_FILE", i18n);
		encrypting = new Stri18ng("ENCRYPTING_FILE", i18n);
		created = new Stri18ng("INVITATION_CREATED", i18n);
		giveToContact = new Stri18ng("INVITATION_GIVE_TO_CONTACT", i18n);
		aborted = new Stri18ng("INVITATION_ABORTED", i18n);
		error = new Stri18ng("INVITATION_ERROR", i18n);
		notFound = new Stri18ng("DIRECTORY_NOT_FOUND", i18n);
		notDir = new Stri18ng("FILE_NOT_DIRECTORY", i18n);
		notAllowed = new Stri18ng("DIRECTORY_NOT_WRITABLE", i18n);
	}

	@Override
	protected void backButtonPressed() {
		assert false;
	}

	@Override
	protected void nextButtonPressed() {
		assert false;
	}

	@Override
	protected void finishButtonPressed() {
		wizard.close();
	}

	@Override
	public void cancelled() {
		wizard.close();
	}

	@Override
	public void finished() {
		wizard.setFinished(true);
	}

	@Override
	protected Runnable getWorker() {
		return workerFactory.createWorker(this, parameters);
	}

	public boolean isCancelled() {
		return cancelled.get();
	}

	public void copyingFile(File f) {
		String path = StringUtils.tail(f.getPath(), MAX_LINE_LENGTH);
		String html = copying.html(path);
		displayProgress(html);
	}

	public void encryptingFile(File f) {
		String path = StringUtils.tail(f.getPath(), MAX_LINE_LENGTH);
		String html = encrypting.html(path);
		displayProgress(html);
	}

	public void created(List<File> files) {
		StringBuilder s = new StringBuilder();
		for(File f : files) {
			if(s.length() > 0) s.append("<br>");
			s.append(StringUtils.tail(f.getPath(), MAX_LINE_LENGTH));
		}
		String filenames = s.toString();
		String html = created.html(filenames, giveToContact.tr());
		done(html);
	}

	public void error(String message) {
		String html = error.html(message, aborted.tr());
		done(html);
	}

	public void notFound(File f) {
		String path = StringUtils.tail(f.getPath(), MAX_LINE_LENGTH);
		String html = notFound.html(path, aborted.tr());
		done(html);
	}

	public void notDirectory(File f) {
		String path = StringUtils.tail(f.getPath(), MAX_LINE_LENGTH);
		String html = notDir.html(path, aborted.tr());
		done(html);
	}

	public void notAllowed(File f) {
		String path = StringUtils.tail(f.getPath(), MAX_LINE_LENGTH);
		String html = notAllowed.html(path, aborted.tr());
		done(html);
	}
}
