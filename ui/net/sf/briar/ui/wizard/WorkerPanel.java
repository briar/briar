package net.sf.briar.ui.wizard;

import java.awt.Dimension;
import java.awt.Font;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import net.sf.briar.api.i18n.Stri18ng;

public abstract class WorkerPanel extends WizardPanel {

	private static final long serialVersionUID = -3761407066345183330L;

	private final Stri18ng starting, cancelling;
	private final JLabel label;
	private final JProgressBar progress;
	private final AtomicBoolean started;

	protected final AtomicBoolean cancelled;

	protected WorkerPanel(Wizard wizard, String id, Stri18ng starting,
			Stri18ng cancelling) {
		super(wizard, id);
		this.starting = starting;
		this.cancelling = cancelling;
		label = new JLabel(starting.html());
		Dimension d = wizard.getPreferredSize();
		label.setPreferredSize(new Dimension(d.width - 50, d.height - 120));
		label.setVerticalAlignment(SwingConstants.TOP);
		add(label);
		progress = new JProgressBar();
		progress.setIndeterminate(true);
		progress.setPreferredSize(new Dimension(d.width - 50, 20));
		add(progress);
		started = new AtomicBoolean(false);
		cancelled = new AtomicBoolean(false);
	}

	public void localeChanged(Font uiFont) {
		label.setText(starting.html());
		label.setFont(uiFont);
	}

	public abstract void cancelled();

	public abstract void finished();

	protected abstract Runnable getWorker();

	@Override
	protected void display() {
		if(!started.getAndSet(true)) {
			wizard.setBackButtonEnabled(false);
			wizard.setNextButtonEnabled(false);
			wizard.setFinished(false);
			new Thread(getWorker()).start();
		}
	}

	@Override
	protected void cancelButtonPressed() {
		if(!cancelled.getAndSet(true)) {
			wizard.setBackButtonEnabled(false);
			wizard.setNextButtonEnabled(false);
			label.setText(cancelling.html());
		}
	}

	public void displayProgress(final String message) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				label.setText("<html>" + message + "</html>");
			}
		});
	}

	public void done(final String message) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				progress.setVisible(false);
				label.setText("<html>" + message + "</html>");
				finished();
			}
		});
	}
}
