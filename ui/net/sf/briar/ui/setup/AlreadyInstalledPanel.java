package net.sf.briar.ui.setup;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingConstants;

import net.sf.briar.api.i18n.I18n;
import net.sf.briar.api.i18n.Stri18ng;
import net.sf.briar.ui.wizard.WizardPanel;

class AlreadyInstalledPanel extends WizardPanel {

	private static final long serialVersionUID = 7908954905165031678L;

	private final Stri18ng question, yes, no;
	private final JLabel label;
	private final JRadioButton yesButton, noButton;

	AlreadyInstalledPanel(SetupWizard wizard, I18n i18n) {
		super(wizard, "AlreadyInstalled");
		question = new Stri18ng("SETUP_ALREADY_INSTALLED", i18n);
		yes = new Stri18ng("YES", i18n);
		no = new Stri18ng("NO", i18n);
		label = new JLabel(question.html());
		Dimension d = wizard.getPreferredSize();
		label.setPreferredSize(new Dimension(d.width - 50, 50));
		label.setVerticalAlignment(SwingConstants.TOP);
		add(label);
		yesButton = new JRadioButton(yes.tr());
		yesButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				AlreadyInstalledPanel.this.wizard.setNextButtonEnabled(true);
			}
		});
		noButton = new JRadioButton(no.tr());
		noButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				AlreadyInstalledPanel.this.wizard.setNextButtonEnabled(true);
			}
		});
		ButtonGroup group = new ButtonGroup();
		group.add(yesButton);
		group.add(noButton);
		JPanel buttonPanel = new JPanel(new GridLayout(2, 1));
		buttonPanel.add(yesButton);
		buttonPanel.add(noButton);
		add(buttonPanel);
	}

	public void localeChanged(Font uiFont) {
		label.setText(question.html());
		label.setFont(uiFont);
		yesButton.setText(yes.tr());
		yesButton.setFont(uiFont);
		noButton.setText(no.tr());
		noButton.setFont(uiFont);
	}

	@Override
	protected void display() {
		wizard.setBackButtonEnabled(true);
		wizard.setNextButtonEnabled(yesButton.isSelected() || noButton.isSelected());
		wizard.setFinished(false);
	}

	@Override
	protected void backButtonPressed() {
		wizard.showPanel("Language");
	}

	@Override
	protected void nextButtonPressed() {
		if(yesButton.isSelected()) wizard.showPanel("Instructions");
		else if(noButton.isSelected()) wizard.showPanel("Location");
		else assert false;
	}

	@Override
	protected void cancelButtonPressed() {
		wizard.close();
	}

	@Override
	protected void finishButtonPressed() {
		assert false;
	}
}
