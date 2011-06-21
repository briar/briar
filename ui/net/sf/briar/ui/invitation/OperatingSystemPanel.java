package net.sf.briar.ui.invitation;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingConstants;

import net.sf.briar.api.i18n.I18n;
import net.sf.briar.api.i18n.Stri18ng;
import net.sf.briar.ui.wizard.Wizard;
import net.sf.briar.ui.wizard.WizardPanel;

import com.google.inject.Inject;

class OperatingSystemPanel extends WizardPanel {

	private static final long serialVersionUID = -8370132633634629466L;

	private final Stri18ng question, windows, mac, linux, unknown;
	private final JLabel questionLabel;
	private final JRadioButton windowsButton, macButton, linuxButton;
	private final JRadioButton unknownButton;

	@Inject
	OperatingSystemPanel(Wizard wizard, I18n i18n) {
		super(wizard, "OperatingSystem");
		question = new Stri18ng("INVITATION_OPERATING_SYSTEM", i18n);
		windows = new Stri18ng("WINDOWS", i18n);
		mac = new Stri18ng("MAC", i18n);
		linux = new Stri18ng("LINUX", i18n);
		unknown = new Stri18ng("UNKNOWN", i18n);
		questionLabel = new JLabel(question.html());
		Dimension d = wizard.getPreferredSize();
		questionLabel.setPreferredSize(new Dimension(d.width - 50, 50));
		questionLabel.setVerticalAlignment(SwingConstants.TOP);
		add(questionLabel);
		windowsButton = new JRadioButton(windows.tr());
		macButton = new JRadioButton(mac.tr());
		linuxButton = new JRadioButton(linux.tr());
		unknownButton = new JRadioButton(unknown.tr());
		ButtonGroup group = new ButtonGroup();
		group.add(windowsButton);
		group.add(macButton);
		group.add(linuxButton);
		group.add(unknownButton);
		unknownButton.setSelected(true);
		JPanel buttonPanel = new JPanel(new GridLayout(4, 1));
		buttonPanel.add(windowsButton);
		buttonPanel.add(macButton);
		buttonPanel.add(linuxButton);
		buttonPanel.add(unknownButton);
		add(buttonPanel);
	}

	public void localeChanged(Font uiFont) {
		questionLabel.setText(question.html());
		questionLabel.setFont(uiFont);
		windowsButton.setText(windows.tr());
		windowsButton.setFont(uiFont);
		macButton.setText(mac.tr());
		macButton.setFont(uiFont);
		linuxButton.setText(linux.tr());
		linuxButton.setFont(uiFont);
	}

	@Override
	protected void display() {
		wizard.setBackButtonEnabled(true);
		wizard.setNextButtonEnabled(true);
		wizard.setFinished(false);
	}

	@Override
	protected void backButtonPressed() {
		wizard.showPanel("ExistingUser");
	}

	@Override
	protected void nextButtonPressed() {
		wizard.showPanel("Password");
	}

	@Override
	protected void cancelButtonPressed() {
		wizard.close();
	}

	@Override
	protected void finishButtonPressed() {
		assert false;
	}

	boolean shouldCreateExe() {
		return windowsButton.isSelected() || unknownButton.isSelected();
	}

	boolean shouldCreateJar() {
		return !windowsButton.isSelected();
	}
}
