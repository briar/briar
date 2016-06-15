package org.briarproject.android.util;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.os.Build;
import android.view.ViewGroup;

import static android.view.View.GONE;
import static android.view.View.MeasureSpec.UNSPECIFIED;
import static android.view.View.VISIBLE;

public class CustomAnimations {

	public static void animateHeight(ViewGroup viewGroup, boolean isExtending,
			int duration) {
		if (Build.VERSION.SDK_INT >= 11) {
			animateHeightPostGingerbread(viewGroup, isExtending, duration);
		} else {
			animateHeightGingerbread(viewGroup, isExtending);
		}
	}

	private static void animateHeightGingerbread(ViewGroup viewGroup,
			boolean isExtending) {
		// No animations for Gingerbread
		if (isExtending) viewGroup.setVisibility(VISIBLE);
		else viewGroup.setVisibility(GONE);
	}


	@SuppressLint("NewApi")
	private static void animateHeightPostGingerbread(final ViewGroup viewGroup,
			final boolean isExtending, int duration) {
		ValueAnimator anim;
		if (isExtending) {
			viewGroup.setVisibility(VISIBLE);
			viewGroup.measure(UNSPECIFIED, UNSPECIFIED);
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
					viewGroup.setVisibility(GONE);
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
