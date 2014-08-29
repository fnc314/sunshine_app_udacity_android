package com.fnc314.sunshine.app;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.RingtonePreference;
import android.text.TextUtils;


import java.util.List;


public class SettingsActivity extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener{

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        /*
        add 'general' preference, defined in the XML file
        */
        // Deprecated method ~> targeting Gingerbread devices
        addPreferencesFromResource(R.xml.pref_general);
        /*
        for all preference attached on OnPreferenceChangeListener so the UI summary can be
        updated when the preference changes.
         */
        // Deprecated method ~> targeting Gingerbread devices
        bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_location_key)));
        bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_units_key)));
    }

    /*
    attaches a listener so the summary is always updated with the preference value.
    Also fires the listener once, to initialize the summary (so it shows up before the value is changed)
     */
    private void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(this);

        // Trigger the listener immediately with the preference's current value
        onPreferenceChange(preference,
                PreferenceManager
                .getDefaultSharedPreferences(preference.getContext())
                .getString(preference.getKey(), ""));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        String stringValue = value.toString();

        if (preference instanceof ListPreference) {
            // for list preference, look up the correct display value in
            // the preference's `entries` list (since they have separate label/value pairs)
            ListPreference listPreference = (ListPreference) preference;
            int prefIndex = listPreference.findIndexOfValue(stringValue);
            if (prefIndex >= 0) {
                preference.setSummary(listPreference.getEntries()[prefIndex]);
            } else {
                // For other preferences, set the summary to the value's simple string representation
                preference.setSummary(stringValue);
            }
        }
        return true;
    }
}
