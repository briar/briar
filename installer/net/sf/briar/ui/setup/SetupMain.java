package net.sf.briar.ui.setup;

import java.util.Locale;

import javax.swing.UIManager;

import net.sf.briar.api.i18n.FontManager;
import net.sf.briar.api.i18n.I18n;
import net.sf.briar.api.setup.SetupParameters;
import net.sf.briar.api.setup.SetupWorkerFactory;
import net.sf.briar.i18n.FontManagerImpl;
import net.sf.briar.i18n.I18nImpl;
import net.sf.briar.setup.SetupWorkerFactoryImpl;
import net.sf.briar.util.OsUtils;

public class SetupMain {

	public static void main(String[] args) throws Exception {
		if(OsUtils.isWindows() || OsUtils.isMac())
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

		FontManager fontManager = new FontManagerImpl();
		I18n i18n = new I18nImpl(fontManager);
		SetupWorkerFactory workerFactory = new SetupWorkerFactoryImpl(i18n);
		SetupWizard wizard = new SetupWizard(i18n);
		new LanguagePanel(wizard, fontManager, i18n);
		new AlreadyInstalledPanel(wizard, i18n);
		new InstructionsPanel(wizard, i18n);
		LocationPanel locationPanel = new LocationPanel(wizard, i18n);
		SetupParameters parameters = new SetupParametersImpl(locationPanel);
		new SetupWorkerPanel(wizard, workerFactory, parameters, i18n);

		fontManager.initialize(Locale.getDefault());
		wizard.display();
	}
}
