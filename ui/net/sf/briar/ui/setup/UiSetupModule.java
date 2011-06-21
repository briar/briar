package net.sf.briar.ui.setup;

import net.sf.briar.api.i18n.FontManager;
import net.sf.briar.api.i18n.I18n;
import net.sf.briar.api.setup.SetupParameters;
import net.sf.briar.api.setup.SetupWorkerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class UiSetupModule extends AbstractModule {

	@Override
	protected void configure() {}

	@Provides @Singleton
	SetupWizard getSetupWizard(I18n i18n, FontManager fontManager,
			SetupWorkerFactory workerFactory) {
		SetupWizard wizard = new SetupWizard(i18n);
		new LanguagePanel(wizard, fontManager, i18n);
		new AlreadyInstalledPanel(wizard, i18n);
		new InstructionsPanel(wizard, i18n);
		LocationPanel locationPanel = new LocationPanel(wizard, i18n);
		SetupParameters parameters =
			new SetupParametersImpl(locationPanel, fontManager);
		new SetupWorkerPanel(wizard, workerFactory, parameters, i18n);
		return wizard;
	}
}
