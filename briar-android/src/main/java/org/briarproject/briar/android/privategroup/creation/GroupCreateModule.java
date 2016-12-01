package org.briarproject.briar.android.privategroup.creation;

import org.briarproject.briar.android.activity.ActivityScope;

import dagger.Module;
import dagger.Provides;

@Module
public class GroupCreateModule {

	@ActivityScope
	@Provides
	CreateGroupController provideCreateGroupController(
			CreateGroupControllerImpl createGroupController) {
		return createGroupController;
	}

}
