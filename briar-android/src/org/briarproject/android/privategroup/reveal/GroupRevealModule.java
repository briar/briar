package org.briarproject.android.privategroup.reveal;

import org.briarproject.android.ActivityScope;

import dagger.Module;
import dagger.Provides;

@Module
public class GroupRevealModule {

	@ActivityScope
	@Provides
	RevealContactsController provideRevealContactsController(
			RevealContactsControllerImpl revealContactsController) {
		return revealContactsController;
	}
}
