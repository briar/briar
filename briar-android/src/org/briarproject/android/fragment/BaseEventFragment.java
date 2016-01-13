package org.briarproject.android.fragment;

import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;

import javax.inject.Inject;

/**
 * Created by Ernir Erlingsson (ernir@ymirmobile.com) on 8.1.2016.
 */
public abstract class BaseEventFragment extends BaseFragment implements
		EventListener {

	@Inject
	protected volatile EventBus eventBus;

	@Override
	public void onResume() {
		super.onResume();
		eventBus.addListener(this);
	}

	@Override
	public void onPause() {
		super.onPause();
		eventBus.removeListener(this);
	}
}
