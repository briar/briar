package org.briarproject.briar.android.conversation.glide;

import android.graphics.Bitmap;

import com.bumptech.glide.load.MultiTransformation;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;

public class BriarImageTransformation extends MultiTransformation<Bitmap> {

	public BriarImageTransformation(int smallRadius, int radius,
			boolean leftCornerSmall, boolean bottomRound) {
		super(new CenterCrop(), new ImageCornerTransformation(
				smallRadius, radius, leftCornerSmall, bottomRound));
	}

}
