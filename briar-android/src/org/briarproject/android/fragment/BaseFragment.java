package org.briarproject.android.fragment;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v4.app.Fragment;

import org.briarproject.android.ActivityComponent;
import org.briarproject.android.DestroyableActivity;

public abstract class BaseFragment extends Fragment {

	protected BaseFragmentListener listener;

	public abstract String getUniqueTag();

	public abstract void injectFragment(ActivityComponent component);

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
	}


	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		this.injectFragment(listener.getActivityComponent());
		listener.onFragmentCreated(getUniqueTag());
	}

	@UiThread
	protected void finish() {
		getActivity().supportFinishAfterTransition();
	}

	public interface BaseFragmentListener extends DestroyableActivity {

		@UiThread
		void showLoadingScreen(boolean isBlocking, int stringId);

		@UiThread
		void hideLoadingScreen();

		void runOnDbThread(Runnable runnable);

		@UiThread
		ActivityComponent getActivityComponent();

		@UiThread
		void onFragmentCreated(String tag);
	}
}
