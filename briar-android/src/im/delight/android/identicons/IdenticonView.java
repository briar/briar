package im.delight.android.identicons;

/**
 * Copyright 2014 www.delight.im <info@delight.im>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

abstract public class IdenticonView extends View {


    public IdenticonView(Context context) {
        super(context);
        init();
    }

    public IdenticonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public IdenticonView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @SuppressLint("NewApi")
    protected void init() {
        setWillNotDraw(false);
        if (Build.VERSION.SDK_INT >= 11) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
    }

    public void show(String input) {
        getDelegate().show(input);
        invalidate();
    }

    public void show(int input) {
        show(String.valueOf(input));
    }

    public void show(long input) {
        show(String.valueOf(input));
    }

    public void show(float input) {
        show(String.valueOf(input));
    }

    public void show(double input) {
        show(String.valueOf(input));
    }

    public void show(byte input) {
        show(String.valueOf(input));
    }

    public void show(char input) {
        show(String.valueOf(input));
    }

    public void show(boolean input) {
        show(String.valueOf(input));
    }

    public void show(Object input) {
        if (input == null) {
            getDelegate().show(null);
        } else {
            show(String.valueOf(input));
        }
    }

    protected byte getByte(int index) {
        return getDelegate().getByte(index);
    }

    abstract protected IdenticonBase getDelegate();

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        getDelegate().updateSize(w, h);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int size = Math.min(getMeasuredWidth(), getMeasuredHeight());
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        getDelegate().draw(canvas);
    }

}
