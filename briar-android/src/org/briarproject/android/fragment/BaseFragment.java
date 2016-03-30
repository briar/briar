package org.briarproject.android.fragment;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;

import org.briarproject.android.AndroidComponent;
import org.briarproject.android.BriarApplication;

public abstract class BaseFragment extends Fragment {

	public abstract String getUniqueTag();

	protected BaseFragmentListener listener;

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

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

//		AndroidComponent component =
//				((BriarApplication) getActivity().getApplication())
//						.getApplicationComponent();
//		injectActivity(component);
	}

	public interface BaseFragmentListener {
		void showLoadingScreen(boolean isBlocking, int stringId);

		void hideLoadingScreen();

		void runOnUiThread(Runnable runnable);

		void runOnDbThread(Runnable runnable);
	}

}
