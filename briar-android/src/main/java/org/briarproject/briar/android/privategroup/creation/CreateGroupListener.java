package org.briarproject.briar.android.privategroup.creation;

import android.view.View;

import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;

interface CreateGroupListener extends BaseFragmentListener {

	void onGroupNameChosen(String name);

	void showSoftKeyboard(View view);

	void hideSoftKeyboard(View view);

}
