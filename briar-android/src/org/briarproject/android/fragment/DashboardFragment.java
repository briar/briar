package org.briarproject.android.fragment;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;

import org.briarproject.R;
import org.briarproject.android.AndroidComponent;
import org.briarproject.api.event.Event;
import org.briarproject.api.plugins.PluginManager;

import java.util.logging.Logger;

import javax.inject.Inject;

public class DashboardFragment extends BaseEventFragment {

	public final static String TAG = "DashboardFragment";

	private static final Logger LOG =
			Logger.getLogger(DashboardFragment.class.getName());

	@Inject
	protected PluginManager pluginManager;

	public static DashboardFragment newInstance() {

		Bundle args = new Bundle();

		DashboardFragment fragment = new DashboardFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View contentView =
				inflater.inflate(R.layout.fragment_dashboard, container, false);
		return contentView;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectActivity(AndroidComponent component) {

	}

	@Override
	public void eventOccurred(Event e) {

	}
}
