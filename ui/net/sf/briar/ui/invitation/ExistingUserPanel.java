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

class ExistingUserPanel extends WizardPanel {

	private static final long serialVersionUID = -8536392615847105689L;

	private final Stri18ng question, yes, no, unknown;
	private final JLabel label;
	private final JRadioButton yesButton, noButton, unknownButton;

	ExistingUserPanel(Wizard wizard, I18n i18n) {
		super(wizard, "ExistingUser");
		question = new Stri18ng("INVITATION_EXISTING_USER", i18n);
		yes = new Stri18ng("YES", i18n);
		no = new Stri18ng("NO", i18n);
		unknown = new Stri18ng("UNKNOWN", i18n);
		label = new JLabel(question.html());
		Dimension d = wizard.getPreferredSize();
		label.setPreferredSize(new Dimension(d.width - 50, 50));
		label.setVerticalAlignment(SwingConstants.TOP);
		add(label);
		yesButton = new JRadioButton(yes.tr());
		noButton = new JRadioButton(no.tr());
		unknownButton = new JRadioButton(unknown.tr());
		ButtonGroup group = new ButtonGroup();
		group.add(yesButton);
		group.add(noButton);
		group.add(unknownButton);
		unknownButton.setSelected(true);
		JPanel buttonPanel = new JPanel(new GridLayout(3, 1));
		buttonPanel.add(yesButton);
		buttonPanel.add(noButton);
		buttonPanel.add(unknownButton);
		add(buttonPanel);
	}

	public void localeChanged(Font uiFont) {
		label.setText(question.html());
		label.setFont(uiFont);
		yesButton.setText(yes.tr());
		yesButton.setFont(uiFont);
		noButton.setText(no.tr());
		noButton.setFont(uiFont);
		unknownButton.setText(unknown.tr());
		unknownButton.setFont(uiFont);
	}

	@Override
	protected void display() {
		wizard.setBackButtonEnabled(true);
		wizard.setNextButtonEnabled(true);
		wizard.setFinished(false);
	}

	@Override
	protected void backButtonPressed() {
		wizard.showPanel("Intro");
	}

	@Override
	protected void nextButtonPressed() {
		if(shouldCreateInstaller()) wizard.showPanel("OperatingSystem");
		else wizard.showPanel("Password");
	}

	@Override
	protected void cancelButtonPressed() {
		wizard.close();
	}

	@Override
	protected void finishButtonPressed() {
		assert false;
	}

	boolean shouldCreateInstaller() {
		return !yesButton.isSelected();
	}
}
