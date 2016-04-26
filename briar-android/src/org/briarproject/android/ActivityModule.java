package org.briarproject.android;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import org.briarproject.android.contact.ContactListFragment;
import org.briarproject.android.controller.BriarController;
import org.briarproject.android.controller.BriarControllerImp;
import org.briarproject.android.controller.NavDrawerController;
import org.briarproject.android.controller.NavDrawerControllerImp;
import org.briarproject.android.controller.TransportStateListener;
import org.briarproject.android.forum.ForumListFragment;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.controller.PasswordController;
import org.briarproject.android.controller.PasswordControllerImp;
import org.briarproject.android.controller.SetupController;
import org.briarproject.android.controller.SetupControllerImp;
import org.briarproject.android.controller.ConfigController;
import org.briarproject.android.controller.ConfigControllerImp;
import org.briarproject.android.introduction.ContactChooserFragment;
import org.briarproject.android.introduction.IntroductionMessageFragment;
import org.briarproject.android.keyagreement.ChooseIdentityFragment;
import org.briarproject.android.keyagreement.ShowQrCodeFragment;

import javax.inject.Named;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.android.BriarService.*;

@Module
public class ActivityModule {

	private final BaseActivity activity;

	public ActivityModule(BaseActivity activity) {
		this.activity = activity;
	}

	@ActivityScope
	@Provides
	BaseActivity providesBaseActivity() {
		return activity;
	}

	@ActivityScope
	@Provides
	Activity providesActivity() {
		return activity;
	}

	@ActivityScope
	@Provides
	SetupController provideSetupController(
			SetupControllerImp setupControllerImp) {
		return setupControllerImp;
	}

	@ActivityScope
	@Provides
	ConfigController provideConfigController(
			ConfigControllerImp configControllerImp) {
		return configControllerImp;
	}

	@ActivityScope
	@Provides
	SharedPreferences provideSharedPreferences(Activity activity) {
		return activity.getSharedPreferences("db", Context.MODE_PRIVATE);
	}

	@ActivityScope
	@Provides
	PasswordController providePasswordController(
			PasswordControllerImp passwordControllerImp) {
		return passwordControllerImp;
	}

	@ActivityScope
	@Provides
	BriarController provideBriarController(
			BriarControllerImp briarControllerImp) {
		activity.addLifecycleController(briarControllerImp);
		return briarControllerImp;
	}

	@ActivityScope
	@Provides
	NavDrawerController provideNavDrawerController(
			NavDrawerControllerImp navDrawerControllerImp) {
		activity.addLifecycleController(navDrawerControllerImp);
		if (activity instanceof TransportStateListener) {
			navDrawerControllerImp
					.setTransportListener((TransportStateListener) activity);
		}
		return navDrawerControllerImp;
	}

	@ActivityScope
	@Provides
	BriarServiceConnection provideBriarServiceConnection() {
		return new BriarServiceConnection();
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

	@Provides
	@Named("ContactChooserFragment")
	BaseFragment provideContactChooserFragment() {
		ContactChooserFragment fragment = new ContactChooserFragment();
		fragment.setArguments(new Bundle());
		return fragment;
	}

	@Provides
	@Named("IntroductionMessageFragment")
	IntroductionMessageFragment provideIntroductionMessageFragment() {
		IntroductionMessageFragment fragment = new IntroductionMessageFragment();
		fragment.setArguments(new Bundle());
		return fragment;
	}

}
