package org.briarproject.android.privategroup.conversation;

import org.briarproject.android.ActivityScope;
import org.briarproject.android.BaseActivity;

import dagger.Module;
import dagger.Provides;

@Module
public class GroupConversationModule {

	@ActivityScope
	@Provides
	GroupController provideGroupController(BaseActivity activity,
			GroupControllerImpl groupController) {
		activity.addLifecycleController(groupController);
		return groupController;
	}
}
