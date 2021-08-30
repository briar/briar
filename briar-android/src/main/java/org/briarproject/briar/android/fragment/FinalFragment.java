package org.briarproject.briar.android.fragment;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ImageViewCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import static android.view.View.FOCUS_DOWN;
import static android.view.View.GONE;

/**
 * A fragment to be used at the end of a user flow
 * where the user should not have the option to go back.
 * Here, we only show final information
 * before finishing the related activity.
 */
@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class FinalFragment extends Fragment {

	public static final String TAG = FinalFragment.class.getName();

	public static final String ARG_TITLE = "title";
	public static final String ARG_ICON = "icon";
	public static final String ARG_ICON_TINT = "iconTint";
	public static final String ARG_TEXT = "text";

	public static FinalFragment newInstance(
			@StringRes int title,
			@DrawableRes int icon,
			@ColorRes int iconTint,
			@StringRes int text) {
		FinalFragment f = new FinalFragment();
		Bundle args = new Bundle();
		args.putInt(ARG_TITLE, title);
		args.putInt(ARG_ICON, icon);
		args.putInt(ARG_ICON_TINT, iconTint);
		args.putInt(ARG_TEXT, text);
		f.setArguments(args);
		return f;
	}

	private NestedScrollView scrollView;
	protected Button buttonView;

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View v = inflater
				.inflate(R.layout.fragment_final, container, false);

		scrollView = (NestedScrollView) v;
		ImageView iconView = v.findViewById(R.id.iconView);
		TextView titleView = v.findViewById(R.id.titleView);
		TextView textView = v.findViewById(R.id.textView);
		buttonView = v.findViewById(R.id.button);

		Bundle args = requireArguments();
		titleView.setText(args.getInt(ARG_TITLE));
		iconView.setImageResource(args.getInt(ARG_ICON));
		int color = getResources().getColor(args.getInt(ARG_ICON_TINT));
		ColorStateList tint = ColorStateList.valueOf(color);
		ImageViewCompat.setImageTintList(iconView, tint);
		int textRes = args.getInt(ARG_TEXT);
		if (textRes == 0) {
			textView.setVisibility(GONE);
		} else {
			textView.setText(textRes);
		}

		buttonView.setOnClickListener(view -> onBackButtonPressed());

		AppCompatActivity a = (AppCompatActivity) requireActivity();
		a.setTitle(args.getInt(ARG_TITLE));
		ActionBar actionBar = a.getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(false);
			actionBar.setHomeButtonEnabled(false);
		}
		a.getOnBackPressedDispatcher().addCallback(
				getViewLifecycleOwner(), new OnBackPressedCallback(true) {
					@Override
					public void handleOnBackPressed() {
						onBackButtonPressed();
					}
				});
		return v;
	}

	@Override
	public void onStart() {
		super.onStart();
		// Scroll down in case the screen is small, so the button is visible
		scrollView.post(() -> scrollView.fullScroll(FOCUS_DOWN));
	}

	/**
	 * This is the action that the system back button
	 * and the button at the bottom will perform.
	 */
	protected void onBackButtonPressed() {
		requireActivity().supportFinishAfterTransition();
	}

}
