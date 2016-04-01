package org.briarproject.android;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import org.briarproject.android.contact.ContactListFragment;
import org.briarproject.android.forum.ForumListFragment;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.helper.PasswordHelper;
import org.briarproject.android.helper.PasswordHelperImp;
import org.briarproject.android.helper.SetupHelper;
import org.briarproject.android.helper.SetupHelperImp;
import org.briarproject.android.helper.ConfigHelper;
import org.briarproject.android.helper.ConfigHelperImp;
import org.briarproject.android.keyagreement.ChooseIdentityFragment;
import org.briarproject.android.keyagreement.ShowQrCodeFragment;

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
	SetupHelper provideSetupHelper(SetupHelperImp setupHelperImp) {
		return setupHelperImp;
	}

	@ActivityScope
	@Provides
	ConfigHelper provideConfigHelper(ConfigHelperImp configHelperImp) {
		return configHelperImp;
	}

	@ActivityScope
	@Provides
	SharedPreferences provideSharedPreferences(Activity activity) {
		return activity.getSharedPreferences("db", Context.MODE_PRIVATE);
	}

	@ActivityScope
	@Provides
	PasswordHelper providePasswordHelper(PasswordHelperImp passwordHelperImp) {
		return passwordHelperImp;
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

	@Provides
	@Named("ChooseIdentityFragment")
	BaseFragment provideChooseIdendityFragment() {
		ChooseIdentityFragment fragment = new ChooseIdentityFragment();
		fragment.setArguments(new Bundle());
		return fragment;
	}

	@Provides
	@Named("ShowQrCodeFragment")
	BaseFragment provideShowQrCodeFragment() {
		ShowQrCodeFragment fragment = new ShowQrCodeFragment();
		fragment.setArguments(new Bundle());
		return fragment;
	}


}
