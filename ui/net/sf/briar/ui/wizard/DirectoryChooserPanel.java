package net.sf.briar.ui.wizard;

import java.io.File;

import javax.swing.JFileChooser;

import net.sf.briar.api.i18n.I18n;
import net.sf.briar.api.i18n.Stri18ng;

public class DirectoryChooserPanel extends TextPanel {

	private static final long serialVersionUID = 6692353522360807409L;

	private final String prevId, nextId;
	private final Stri18ng title;
	private final I18n i18n;
	private volatile File chosenDirectory = null;

	protected DirectoryChooserPanel(Wizard wizard, String id, String prevId,
			String nextId, Stri18ng title, Stri18ng text, I18n i18n) {
		super(wizard, id, text);
		this.prevId = prevId;
		this.nextId = nextId;
		this.title = title;
		this.i18n = i18n;
	}

	@Override
	protected void display() {
		wizard.setBackButtonEnabled(true);
		wizard.setNextButtonEnabled(true);
		wizard.setFinished(false);
	}

	@Override
	protected void backButtonPressed() {
		wizard.showPanel(prevId);
	}

	@Override
	protected void nextButtonPressed() {
		JFileChooser chooser;
		String home = System.getProperty("user.home");
		if(home == null) chooser = new JFileChooser();
		else chooser = new JFileChooser(home);
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setDialogTitle(title.tr());
		chooser.setComponentOrientation(i18n.getComponentOrientation());
		int result = chooser.showSaveDialog(this);
		if(result == JFileChooser.APPROVE_OPTION) {
			File dir = chooser.getSelectedFile();
			assert dir != null;
			assert dir.exists();
			assert dir.isDirectory();
			chosenDirectory = dir;
			wizard.showPanel(nextId);
		}
	}

	@Override
	protected void cancelButtonPressed() {
		wizard.close();
	}

	@Override
	protected void finishButtonPressed() {
		assert false;
	}

	public File getChosenDirectory() {
		return chosenDirectory;
	}
}
