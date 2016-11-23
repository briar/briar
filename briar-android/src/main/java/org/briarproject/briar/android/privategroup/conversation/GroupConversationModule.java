package org.briarproject.briar.android.privategroup.conversation;

import org.briarproject.briar.android.activity.ActivityScope;
import org.briarproject.briar.android.activity.BaseActivity;

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
