package com.google.android.material.tabs;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

/** コンパイル専用スタブ。 */
public class TabLayout extends HorizontalScrollView {
    public TabLayout(Context context) {
        super(context);
    }

    public int getTabCount() {
        return 0;
    }

    public Tab getTabAt(int index) {
        return null;
    }

    public ColorStateList getTabTextColors() {
        return null;
    }

    public void setTabTextColors(int normalColor, int selectedColor) {
    }

    public static class Tab {
        public TabView view;
    }

    public final class TabView extends LinearLayout {
        public TabView(Context context) {
            super(context);
        }
    }
}
