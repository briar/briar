package net.sf.briar.ui.setup;

import java.io.File;

import net.sf.briar.api.i18n.I18n;
import net.sf.briar.api.i18n.Stri18ng;
import net.sf.briar.api.setup.SetupCallback;
import net.sf.briar.api.setup.SetupParameters;
import net.sf.briar.api.setup.SetupWorkerFactory;
import net.sf.briar.ui.wizard.WorkerPanel;
import net.sf.briar.util.StringUtils;

class SetupWorkerPanel extends WorkerPanel implements SetupCallback {

	private static final long serialVersionUID = 6596714579098160155L;

	private static final int MAX_LINE_LENGTH = 40;

	private final SetupWorkerFactory workerFactory;
	private final SetupParameters parameters;
	private final Stri18ng extracting, copying, installed, uninstall;
	private final Stri18ng aborted, error, notFound, notDir, notAllowed;

	SetupWorkerPanel(SetupWizard wizard, SetupWorkerFactory workerFactory,
			SetupParameters parameters, I18n i18n) {
		super(wizard, "SetupWorker",
				new Stri18ng("SETUP_PROGRESS_BEGIN", i18n),
				new Stri18ng("CANCELLING", i18n));
		this.workerFactory = workerFactory;
		this.parameters = parameters;
		extracting = new Stri18ng("EXTRACTING_FILE", i18n);
		copying = new Stri18ng("COPYING_FILE", i18n);
		installed = new Stri18ng("SETUP_INSTALLED", i18n);
		uninstall = new Stri18ng("SETUP_UNINSTALL", i18n);
		aborted = new Stri18ng("SETUP_ABORTED", i18n);
		error = new Stri18ng("SETUP_ERROR", i18n);
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
		System.exit(0);
	}

	@Override
	public void cancelled() {
		System.exit(0);
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

	public void extractingFile(File f) {
		String path = StringUtils.tail(f.getPath(), MAX_LINE_LENGTH);
		String html = extracting.html(path);
		displayProgress(html);
	}

	public void copyingFile(File f) {
		String path = StringUtils.tail(f.getPath(), MAX_LINE_LENGTH);
		String html = copying.html(path);
		displayProgress(html);
	}

	public void installed(File f) {
		String path = StringUtils.tail(f.getPath(), MAX_LINE_LENGTH);
		String html = installed.html(path, uninstall.tr());
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
