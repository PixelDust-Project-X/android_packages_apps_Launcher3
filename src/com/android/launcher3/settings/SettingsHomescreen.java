/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3.settings;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.graphics.GridOptionsProvider;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.util.SecureSettingsObserver;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceFragment.OnPreferenceStartFragmentCallback;
import androidx.preference.PreferenceFragment.OnPreferenceStartScreenCallback;
import androidx.preference.PreferenceGroup.PreferencePositionCallback;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Settings activity for Launcher. Currently implements the following setting: Allow rotation
 */
public class SettingsHomescreen extends Activity
        implements OnPreferenceStartFragmentCallback, OnPreferenceStartScreenCallback,
        SharedPreferences.OnSharedPreferenceChangeListener{

    public static final String EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key";
    public static final String EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args";
    private static final int DELAY_HIGHLIGHT_DURATION_MILLIS = 600;
    public static final String SAVE_HIGHLIGHTED_KEY = "android:preference_highlighted";

    public static final String GRID_OPTIONS_PREFERENCE_KEY = "pref_grid_options";

    @Override
    protected void onCreate(final Bundle bundle) {
        super.onCreate(bundle);
        if (bundle == null) {
            getFragmentManager().beginTransaction().replace(android.R.id.content, new HomescreenSettingsFragment()).commit();
        }
        Utilities.getPrefs(getApplicationContext()).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (GRID_OPTIONS_PREFERENCE_KEY.equals(key)) {

            final ComponentName cn = new ComponentName(getApplicationContext(),
                    GridOptionsProvider.class);
            Context c = getApplicationContext();
            int oldValue = c.getPackageManager().getComponentEnabledSetting(cn);
            int newValue;
            if (Utilities.getPrefs(c).getBoolean(GRID_OPTIONS_PREFERENCE_KEY, false)) {
                newValue = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
            } else {
                newValue = PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            }

            if (oldValue != newValue) {
                c.getPackageManager().setComponentEnabledSetting(cn, newValue,
                        PackageManager.DONT_KILL_APP);
            }
        } else if (Utilities.KEY_SHOW_SEARCHBAR.equals(key)) {
            LauncherAppState.getInstanceNoCreate().setNeedsRestart();
        }
    }

    private boolean startFragment(String fragment, Bundle args, String key) {
        if (Utilities.ATLEAST_P && getFragmentManager().isStateSaved()) {
            // Sometimes onClick can come after onPause because of being posted on the handler.
            // Skip starting new fragments in that case.
            return false;
        }
        Fragment f = Fragment.instantiate(this, fragment, args);
        if (f instanceof DialogFragment) {
            ((DialogFragment) f).show(getFragmentManager(), key);
        } else {
            getFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, f)
                    .addToBackStack(key)
                    .commit();
        }
        return true;
    }

    @Override
    public boolean onPreferenceStartFragment(
            PreferenceFragment preferenceFragment, Preference pref) {
        return startFragment(pref.getFragment(), pref.getExtras(), pref.getKey());
    }

    @Override
    public boolean onPreferenceStartScreen(PreferenceFragment caller, PreferenceScreen pref) {
        Bundle args = new Bundle();
        args.putString(PreferenceFragment.ARG_PREFERENCE_ROOT, pref.getKey());
        return startFragment(getString(R.string.home_category_title), args, pref.getKey());
    }

    /**
     * This fragment shows the launcher preferences.
     */
    public static class HomescreenSettingsFragment extends PreferenceFragment {

        private String mHighLightKey;
        private boolean mPreferenceHighlighted = false;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            final Bundle args = getArguments();

            if (savedInstanceState != null) {
                mPreferenceHighlighted = savedInstanceState.getBoolean(SAVE_HIGHLIGHTED_KEY);
            }

            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            setPreferencesFromResource(R.xml.home_screen_preferences, rootKey);

            PreferenceScreen screen = getPreferenceScreen();
            for (int i = screen.getPreferenceCount() - 1; i >= 0; i--) {
                Preference preference = screen.getPreference(i);
                if (!initPreference(preference)) {
                    screen.removePreference(preference);
                }
            }
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putBoolean(SAVE_HIGHLIGHTED_KEY, mPreferenceHighlighted);
        }

        protected String getParentKeyForPref(String key) {
            return null;
        }

        /**
         * Initializes a preference. This is called for every preference. Returning false here
         * will remove that preference from the list.
         */
        protected boolean initPreference(Preference preference) {
            switch (preference.getKey()) {
                case GRID_OPTIONS_PREFERENCE_KEY:
                    return Utilities.isDevelopersOptionsEnabled(getContext()) &&
                            Utilities.IS_DEBUG_DEVICE &&
                            Utilities.existsStyleWallpapers(getContext());
            }
            return true;
        }

        @Override
        public void onResume() {
            super.onResume();

            if (isAdded() && !mPreferenceHighlighted) {
                PreferenceHighlighter highlighter = createHighlighter();
                if (highlighter != null) {
                    getView().postDelayed(highlighter, DELAY_HIGHLIGHT_DURATION_MILLIS);
                    mPreferenceHighlighted = true;
                }
            }
        }

        private PreferenceHighlighter createHighlighter() {
            if (TextUtils.isEmpty(mHighLightKey)) {
                return null;
            }

            PreferenceScreen screen = getPreferenceScreen();
            if (screen == null) {
                return null;
            }

            RecyclerView list = getListView();
            PreferencePositionCallback callback = (PreferencePositionCallback) list.getAdapter();
            int position = callback.getPreferenceAdapterPosition(mHighLightKey);
            return position >= 0 ? new PreferenceHighlighter(list, position) : null;
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
        }
    }
}
