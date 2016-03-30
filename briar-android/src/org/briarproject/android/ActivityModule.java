package org.briarproject.android;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;

import org.briarproject.android.contact.ContactListFragment;
import org.briarproject.android.forum.ForumListFragment;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.sdk.BriarHelper;
import org.briarproject.android.sdk.BriarHelperImp;

import java.util.logging.Logger;

import javax.inject.Named;

import dagger.Module;
import dagger.Provides;

@Module
public class ActivityModule {

	private final BaseActivity activity;

	public ActivityModule(BaseActivity activity) {
		this.activity = activity;
	}

	@ActivityScope
	@Provides
	Activity providesActivity() {
		return activity;
	}

	@ActivityScope
	@Provides
	BriarHelper provideBriarHelper(BriarHelperImp briarHelperImp) {
		return briarHelperImp;
	}

	@ActivityScope
	Logger provideLogger(Activity activity) {
		return Logger.getLogger(activity.getClass().getName());
	}

	@ActivityScope
	@Provides
	SharedPreferences provideSharedPreferences(Activity activity) {
		return activity.getSharedPreferences("db", Context.MODE_PRIVATE);
	}


	@Provides
	@Named("ForumListFragment")
	BaseFragment provideForumListFragment(
			ForumListFragment forumListFragment) {
		forumListFragment.setArguments(new Bundle());
		return forumListFragment;
	}

	@Provides
	@Named("ContactListFragment")
	BaseFragment provideContactListFragment(
			ContactListFragment contactListFragment) {
		contactListFragment.setArguments(new Bundle());
		return contactListFragment;
	}

}
