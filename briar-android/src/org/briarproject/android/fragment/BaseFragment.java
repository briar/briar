package org.briarproject.android.fragment;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v4.app.Fragment;
import android.view.MenuItem;

import org.briarproject.android.ActivityComponent;
import org.briarproject.android.DestroyableContext;

public abstract class BaseFragment extends Fragment
		implements DestroyableContext {

	protected BaseFragmentListener listener;

	public abstract String getUniqueTag();

	public abstract void injectFragment(ActivityComponent component);

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		listener = (BaseFragmentListener) context;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// allow for "up" button to act as back button
		setHasOptionsMenu(true);
	}


	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		injectFragment(listener.getActivityComponent());
		listener.onFragmentCreated(getUniqueTag());
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				listener.onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@UiThread
	protected void finish() {
		getActivity().supportFinishAfterTransition();
	}

	public interface BaseFragmentListener extends DestroyableContext {

		@Deprecated
		void runOnDbThread(Runnable runnable);

		@UiThread
		void onBackPressed();

		@UiThread
		ActivityComponent getActivityComponent();

		@UiThread
		void onFragmentCreated(String tag);
	}

	@Override
	public void runOnUiThreadUnlessDestroyed(Runnable r) {
		listener.runOnUiThreadUnlessDestroyed(r);
	}
}
