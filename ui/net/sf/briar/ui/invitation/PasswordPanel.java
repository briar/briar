package net.sf.briar.ui.invitation;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingConstants;

import net.sf.briar.api.i18n.I18n;
import net.sf.briar.api.i18n.Stri18ng;
import net.sf.briar.ui.wizard.Wizard;
import net.sf.briar.ui.wizard.WizardPanel;

class PasswordPanel extends WizardPanel {

	private static final long serialVersionUID = -1012132977732308293L;

	private final ExistingUserPanel existingUserPanel;
	private final Stri18ng intro, enterPassword, confirmPassword;
	private final JLabel introLabel, enterPasswordLabel, confirmPasswordLabel;
	private final JPasswordField password1, password2;

	PasswordPanel(Wizard wizard, ExistingUserPanel existingUserPanel,
			I18n i18n) {
		super(wizard, "Password");
		this.existingUserPanel = existingUserPanel;
		intro = new Stri18ng("INVITATION_PASSWORD", i18n);
		enterPassword = new Stri18ng("ENTER_PASSWORD", i18n);
		confirmPassword = new Stri18ng("CONFIRM_PASSWORD", i18n);
		introLabel = new JLabel(intro.html());
		Dimension d = wizard.getPreferredSize();
		introLabel.setPreferredSize(
				new Dimension(d.width - 50, d.height - 140));
		introLabel.setVerticalAlignment(SwingConstants.TOP);
		add(introLabel);
		JPanel panel1 = new JPanel(new FlowLayout(FlowLayout.LEADING));
		enterPasswordLabel = new JLabel(enterPassword.tr());
		enterPasswordLabel.setPreferredSize(
				new Dimension((d.width - 60) / 2, 20));
		password1 = new JPasswordField();
		password1.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				checkPasswords();
			}
		});
		password1.setPreferredSize(new Dimension((d.width - 60) / 2, 20));
		panel1.add(enterPasswordLabel);
		panel1.add(password1);
		add(panel1);
		JPanel panel2 = new JPanel(new FlowLayout(FlowLayout.LEADING));
		confirmPasswordLabel = new JLabel(confirmPassword.tr());
		confirmPasswordLabel.setPreferredSize(
				new Dimension((d.width - 60) / 2, 20));
		password2 = new JPasswordField();
		password2.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				checkPasswords();
			}
		});
		password2.setPreferredSize(new Dimension((d.width - 60) / 2, 20));
		panel2.add(confirmPasswordLabel);
		panel2.add(password2);
		add(panel2);
	}

	public void localeChanged(Font uiFont) {
		introLabel.setText(intro.html());
		introLabel.setFont(uiFont);
		enterPasswordLabel.setText(enterPassword.tr());
		enterPasswordLabel.setFont(uiFont);
		confirmPasswordLabel.setText(confirmPassword.tr());
		confirmPasswordLabel.setFont(uiFont);
	}

	private void checkPasswords() {
		wizard.setNextButtonEnabled(passwordsMatch());
	}

	private boolean passwordsMatch() {
		char[] p1 = password1.getPassword();
		char[] p2 = password2.getPassword();
		assert p1 != null && p2 != null;
		boolean ok = p1.length > 3 && p2.length > 3 && Arrays.equals(p1, p2);
		Arrays.fill(p1, (char) 0);
		Arrays.fill(p2, (char) 0);
		return ok;
	}

	@Override
	protected void display() {
		wizard.setBackButtonEnabled(true);
		wizard.setNextButtonEnabled(false);
		wizard.setFinished(false);
		password1.setText("");
		password2.setText("");
	}

	@Override
	protected void backButtonPressed() {
		if(existingUserPanel.shouldCreateInstaller())
			wizard.showPanel("OperatingSystem");
		else wizard.showPanel("ExistingUser");
	}

	@Override
	protected void nextButtonPressed() {
		wizard.showPanel("Location");
	}

	@Override
	protected void cancelButtonPressed() {
		wizard.close();
	}

	@Override
	protected void finishButtonPressed() {
		assert false;
	}

	public char[] getPassword() {
		if(passwordsMatch()) return password1.getPassword();
		else return null;
	}
}
