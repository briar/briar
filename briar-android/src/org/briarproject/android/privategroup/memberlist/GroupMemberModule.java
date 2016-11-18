package org.briarproject.android.privategroup.memberlist;

import org.briarproject.android.ActivityScope;

import dagger.Module;
import dagger.Provides;

@Module
public class GroupMemberModule {

	@ActivityScope
	@Provides
	GroupMemberListController provideGroupMemberListController(
			GroupMemberListControllerImpl groupMemberListController) {
		return groupMemberListController;
	}
}
