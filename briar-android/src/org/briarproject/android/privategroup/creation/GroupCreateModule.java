package org.briarproject.android.privategroup.creation;

import org.briarproject.android.ActivityScope;

import dagger.Module;
import dagger.Provides;

@Module
public class GroupCreateModule {

	@ActivityScope
	@Provides
	protected CreateGroupController provideCreateGroupController(
			CreateGroupControllerImpl createGroupController) {
		return createGroupController;
	}

}
