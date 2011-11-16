package net.sf.briar.ui.setup;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Locale;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.SwingConstants;

import net.sf.briar.api.i18n.FontManager;
import net.sf.briar.api.i18n.I18n;
import net.sf.briar.api.i18n.Stri18ng;
import net.sf.briar.ui.wizard.WizardPanel;

class LanguagePanel extends WizardPanel {

	private static final long serialVersionUID = 6692353522360807409L;

	// FIXME: Does this have to be hardcoded?
	// Not static because we want the fonts to be loaded first
	private final Language english = new Language("English", "en");
	private final Language[] languages = new Language[] {
			new Language("\u0627\u0644\u0639\u0631\u0628\u064a\u0629", "ar"),
			new Language("\u0f60\u0f51\u0f72\u0f60\u0f72\u0f0b\u0f66\u0f90\u0f7c\u0f62\u0f0d", "bo"),
			new Language("\u4e2d\u6587\uff08\u7b80\u4f53\uff09", "cn"),
			english,
			new Language("\u0641\u0627\u0631\u0633\u06cc", "fa"),
			new Language("\u05e2\u05d1\u05e8\u05d9\u05ea", "he"),
			new Language("\u65e5\u672c\u8a9e", "ja"),
			new Language("\ud55c\uad6d\uc5b4", "ko"),
			new Language("\u1006\u102f\u102d\u1010\u1032\u1037", "my"),
			new Language("\u0420\u0443\u0441\u0441\u043a\u0438\u0439", "ru"),
			new Language("Igpay Atinlay", "pg"),
			new Language("\u0e44\u0e17\u0e22", "th"),
			new Language("Ti\u1ebfng Vi\u1ec7t", "vi"),
	};

	private final FontManager fontManager;
	private final Stri18ng language;
	private final JLabel label;
	private final JComboBox comboBox;

	LanguagePanel(SetupWizard wizard, FontManager fontManager,
			final I18n i18n) {
		super(wizard, "Language");
		this.fontManager = fontManager;
		language = new Stri18ng("SETUP_LANGUAGE", i18n);
		label = new JLabel(language.html());
		Dimension d = wizard.getPreferredSize();
		label.setPreferredSize(new Dimension(d.width - 50, d.height - 120));
		label.setVerticalAlignment(SwingConstants.TOP);
		add(label);
		comboBox = new JComboBox();
		for(Language l : languages) comboBox.addItem(l);
		comboBox.setRenderer(new LanguageRenderer());
		comboBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Language l = (Language) comboBox.getSelectedItem();
				i18n.setLocale(new Locale(l.code));
			}
		});
		add(comboBox);
		comboBox.setSelectedItem(english);
	}

	public void localeChanged(Font uiFont) {
		label.setText(language.html());
		label.setFont(uiFont);
		comboBox.setFont(uiFont);
	}

	@Override
	protected void display() {
		wizard.setBackButtonEnabled(false);
		wizard.setNextButtonEnabled(true);
		wizard.setFinished(false);
	}

	@Override
	protected void backButtonPressed() {
		assert false;
	}

	@Override
	protected void nextButtonPressed() {
		wizard.showPanel("AlreadyInstalled");
	}

	@Override
	protected void cancelButtonPressed() {
		System.exit(0);
	}

	@Override
	protected void finishButtonPressed() {
		assert false;
	}

	private static class Language {

		private final String name, code;

		private Language(String name, String code) {
			this.name = name;
			this.code = code;
		}
	}

	private class LanguageRenderer extends JLabel implements ListCellRenderer {

		private static final long serialVersionUID = 8562749521807769004L;

		LanguageRenderer() {
			setHorizontalAlignment(SwingConstants.CENTER);
			setVerticalAlignment(SwingConstants.CENTER);
			setPreferredSize(new Dimension(100, 20));
		}

		public Component getListCellRendererComponent(JList list, Object value,
				int index, boolean isSelected, boolean cellHasFocus) {
			Language language = (Language) value;
			setText(language.name);
			setFont(fontManager.getFontForLanguage(language.code));
			if(isSelected) {
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			} else {
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}
			return this;
		}
	}
}
