package org.briarproject.android.util;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

public class CustomAnimations {

	public static void animateHeight(
			final ViewGroup viewGroup, final boolean isExtending,
			int duration) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			animateHeightPostGingerbread(viewGroup, isExtending, duration);
		} else {
			animateHeightGingerbread(viewGroup, isExtending, duration);
		}
	}

	private static void animateHeightGingerbread(final ViewGroup viewGroup,
			final boolean isExtending, int duration) {
		// No animations for Gingerbread
		if (isExtending) {
			viewGroup.setVisibility(View.VISIBLE);
		} else {
			viewGroup.setVisibility(View.GONE);
		}
	}


	@SuppressLint("NewApi")
	private static void animateHeightPostGingerbread(
			final ViewGroup viewGroup,
			final boolean isExtending,
			int duration) {
		ValueAnimator anim;
		if (isExtending) {
			viewGroup.setVisibility(View.VISIBLE);
			viewGroup.measure(View.MeasureSpec.UNSPECIFIED,
					View.MeasureSpec.UNSPECIFIED);
			anim = ValueAnimator.ofInt(0, viewGroup.getMeasuredHeight());
		} else {
			anim = ValueAnimator.ofInt(viewGroup.getHeight(), 0);
		}
		anim.addListener(new Animator.AnimatorListener() {
			@Override
			public void onAnimationStart(Animator animation) {

			}

			@Override
			public void onAnimationEnd(Animator animation) {
				if (!isExtending) {
					viewGroup.setVisibility(View.GONE);
				}
			}

			@Override
			public void onAnimationCancel(Animator animation) {

			}

			@Override
			public void onAnimationRepeat(Animator animation) {

			}
		});
		anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
			@Override
			public void onAnimationUpdate(ValueAnimator valueAnimator) {
				int val = (Integer) valueAnimator.getAnimatedValue();
				ViewGroup.LayoutParams layoutParams =
						viewGroup.getLayoutParams();
				layoutParams.height = val;
				viewGroup.setLayoutParams(layoutParams);
			}

		});
		anim.setDuration(duration);
		anim.start();
	}
}
