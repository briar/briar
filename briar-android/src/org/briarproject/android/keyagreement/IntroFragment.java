package org.briarproject.android.keyagreement;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ScrollView;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.fragment.BaseFragment;

import static android.view.View.FOCUS_DOWN;

public class IntroFragment extends BaseFragment {

	interface IntroScreenSeenListener {
		void showNextScreen();
	}

	public static final String TAG = IntroFragment.class.getName();

	private IntroScreenSeenListener screenSeenListener;
	private ScrollView scrollView;

	public static IntroFragment newInstance() {
		
		Bundle args = new Bundle();
		
		IntroFragment fragment = new IntroFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		try {
			screenSeenListener = (IntroScreenSeenListener) context;
		} catch (ClassCastException e) {
			throw new ClassCastException(
					"Using class must implement IntroScreenSeenListener");
		}
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		View v = inflater.inflate(R.layout.fragment_keyagreement_id, container,
				false);
		scrollView = (ScrollView) v.findViewById(R.id.scrollView);
		View button = v.findViewById(R.id.continueButton);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				screenSeenListener.showNextScreen();
			}
		});
		return v;
	}

	@Override
	public void onStart() {
		super.onStart();
		scrollView.post(new Runnable() {
			@Override
			public void run() {
				scrollView.fullScroll(FOCUS_DOWN);
			}
		});
	}

}
