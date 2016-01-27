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

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;

import org.briarproject.api.crypto.CryptoComponent;

import javax.inject.Inject;

import roboguice.RoboGuice;

public class SymmetricIdenticon extends IdenticonView {

    private static final int CENTER_COLUMN_INDEX = 5;

    @Inject private CryptoComponent mCrypto;
    private IdenticonBase mDelegate;

    public SymmetricIdenticon(Context context) {
        super(context);
        initDelegate();
    }

    public SymmetricIdenticon(Context context, AttributeSet attrs) {
        super(context, attrs);
        initDelegate();
    }

    public SymmetricIdenticon(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initDelegate();
    }

    private void initDelegate() {
        RoboGuice.injectMembers(getContext(), this);
        mDelegate = new IdenticonBase() {
            @Override
            protected CryptoComponent getCrypto() {
                return mCrypto;
            }

            @Override
            protected int getRowCount() {
                return 9;
            }

            @Override
            protected int getColumnCount() {
                return 9;
            }

            @Override
            protected boolean isCellVisible(int row, int column) {
                return getByte(3 + row * CENTER_COLUMN_INDEX + getSymmetricColumnIndex(column)) >= 0;
            }

            @Override
            protected int getIconColor() {
                return Color.rgb(getByte(0) + 128, getByte(1) + 128, getByte(2) + 128);
            }
        };
    }

    @Override
    protected IdenticonBase getDelegate() {
        return mDelegate;
    }

    protected int getSymmetricColumnIndex(int row) {
        if (row < CENTER_COLUMN_INDEX) {
            return row;
        } else {
            return getDelegate().getColumnCount() - row - 1;
        }
    }

}
