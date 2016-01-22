package org.briarproject.android.fragment;

import android.content.Context;

import org.briarproject.android.BriarActivity;

import roboguice.fragment.RoboFragment;

public abstract class BaseFragment extends RoboFragment {

	public abstract String getUniqueTag();

	protected BaseFragmentListener listener;

	protected BriarActivity briarActivity;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		try {
			listener = (BaseFragmentListener) context;
		} catch (ClassCastException e) {
			throw new ClassCastException(
					"Using class must implement BaseFragmentListener");
		}
	}

	public interface BaseFragmentListener {
		void showLoadingScreen(boolean isBlocking, int stringId);
		void hideLoadingScreen();
		void runOnUiThread(Runnable runnable);
		void runOnDbThread(Runnable runnable);
	}

}
