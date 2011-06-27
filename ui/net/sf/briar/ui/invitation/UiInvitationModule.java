package net.sf.briar.ui.invitation;

import net.sf.briar.api.i18n.I18n;
import net.sf.briar.api.invitation.InvitationParameters;
import net.sf.briar.api.invitation.InvitationWorkerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class UiInvitationModule extends AbstractModule {

	@Override
	protected void configure() {}

	@Provides @Singleton
	InvitationWizard getInvitationWizard(I18n i18n,
			InvitationWorkerFactory workerFactory) {
		InvitationWizard wizard = new InvitationWizard(i18n);
		new IntroPanel(wizard, i18n);
		ExistingUserPanel userPanel = new ExistingUserPanel(wizard, i18n);
		OperatingSystemPanel osPanel = new OperatingSystemPanel(wizard, i18n);
		PasswordPanel passwordPanel =
			new PasswordPanel(wizard, userPanel, i18n);
		LocationPanel locationPanel = new LocationPanel(wizard, i18n);
		InvitationParameters parameters = new InvitationParametersImpl(
				userPanel, osPanel, passwordPanel, locationPanel);
		new InvitationWorkerPanel(wizard, workerFactory, parameters, i18n);
		return wizard;
	}
}
