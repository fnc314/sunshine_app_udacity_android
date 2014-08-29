package com.fnc314.sunshine.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class ForecastFragment extends Fragment {

    private ArrayAdapter<String> mForecastAdapter;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // This line allows this fragment to handle menu events
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here.  The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            updateWeather();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        /*
        removal of dummy data to allow app to automatically update when launched
        String[] forecastArray = {
                "Today - Sunny - 88/63",
                "Tomorrow - Foggy - 70/40",
                "Weds - Cloudy - 72/65",
                "Thurs - Asteroids - 75/65",
                "Fri - Heavy Rain - 65/56",
                "Sat - HELP TRAPPED IN THE WEATHER STATION - 60/51",
                "Sun - Sunny - 80/68"
        };

        following line is no-longer necessary
        // List<String> weekForecast = new ArrayList<String>(Arrays.asList(forecastArray));
        */

        /*
        The ArrayAdapter will take data from a source
        and use it to populate the ListView it's attached to
        */
        mForecastAdapter = new ArrayAdapter<String>(
                // The current context (this fragment's parent activity)
                getActivity(),
                // ID of list item layout
                R.layout.list_item_forecast,
                // ID of the TextView to populate
                R.id.list_item_forecast_textview,
                // Forecast Data ~> replaced to allow app to load new data on start-up
                new ArrayList<String>());

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        // Find the listView and set the adapter to it from the ArrayAdapter created above
        // USE THIS METHOD TO ATTACH LIST OF TEXT FROM SERVER
        ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(mForecastAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String weatherText = mForecastAdapter.getItem(i);

                // Intent to launch DetailActivity.class
                Intent detailActivity = new Intent(getActivity(), DetailActivity.class);
                detailActivity.putExtra(Intent.EXTRA_TEXT, weatherText);
                startActivity(detailActivity);
            }

        });

        return rootView;
    }

    private void updateWeather() {
        FetchWeatherTask fetchWeatherTask = new FetchWeatherTask();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String location = prefs.getString(getString(R.string.pref_location_key),
                getString(R.string.pref_location_default));
        fetchWeatherTask.execute(location);
    }

    @Override
    public void onStart() {
        super.onStart();
        updateWeather();
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]> {

        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();


        // Date/time conversion
        private String getReadableDateString(long time) {
            //  API returns unix timestamp (time in seconds from epoch)
            //  Convert to milliseconds and then to valid date
            Date date = new Date(time * 1000);
            SimpleDateFormat format = new SimpleDateFormat("E, MMM d");
            return format.format(date);
        }

        // Preparation of Weather High/Low for presentation
        private String formatHighLows(double high, double low) {
            // Data is fetched in Celsius
            // If user prefers to see in Fahrenheit, convert values here
            // We do this rather than fetching data from API again
            // Will severely reduce data to store in a database
            SharedPreferences sharedPrefs =
                    PreferenceManager.getDefaultSharedPreferences(getActivity());
            String unitType = sharedPrefs.getString(
                    getString(R.string.pref_units_key),
                    getString(R.string.pref_units_metric)
            );

            if (unitType.equals(getString(R.string.pref_units_imperial))) {
                high = (high * 1.8) + 32;
                low = (low * 1.8) + 32;
            } else if (!unitType.equals(getString(R.string.pref_units_metric))) {
                Log.d(LOG_TAG, "Unit type not found: " + unitType);
            }
            // We assume user doesn't care about tenths (or smaller increments) of a degree
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);
            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }

        /**
         * Take the STRING representing the complete forecast in JSON format and pull ou the data
         * we need to construct the STRINGs needed for the wireframes
         *
         * Fortunately parsing is easy: constructor takes the JSON string and creates the
         * Object hierarchy
         */
        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays) throws JSONException {

            // These are the naems of the JSON objects that need extraction
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DATETIME = "dt";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            String[] resultStrs = new String[numDays]; // This creates a string array of desired length
            for(int i = 0; i < weatherArray.length(); i++) {
                // For now, using the format "Day, description, hi/low"
                String day;
                String description;
                String highAndLow;

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                // The Date/Time is returned as a long, we need to convert that into something
                // more human-readable, since most people won't read "1400356800" as "this Saturday"
                long dateTime = dayForecast.getLong(OWM_DATETIME);
                day = getReadableDateString(dateTime);

                // description is in a child array called "weather" whcih is 1 element long
                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                // Temperatures are in a child oject called "temp".  Try not to name variables "temp"
                // when working with temperature as it is very confusing
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                highAndLow = formatHighLows(temperatureObject.getDouble(OWM_MAX), temperatureObject.getDouble(OWM_MIN));

                resultStrs[i] = day + " - " + description + " - " + highAndLow;
            }

            // Log.v(LOG_TAG, resultStrs[3]);
            return resultStrs;
        }

        @Override
        protected String[] doInBackground(String... params) {

            // If there is no zip code, there is nothing to look up.  Verify input size
            if (params.length == 0) {
                return null;
            }
            // Declare URL connection and BufferedReader outside of try/catch block
            // so they can be closed in the `finally` block
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string
            String forecastJsonStr = null;
            int numDays = 7;
            String format = "json";
            String units = "metric";

            try {
                // Abstracting away the URL for the API call
                // using the following sets and a URI builder
                final String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String QUEARY_PARAM = "q";
                final String FORMAT_PARAM = "mode";
                final String UNITS_PARAM = "units";
                final String DAYS_PARAM = "cnt";

                // Build URI
                Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(QUEARY_PARAM, params[0])
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UNITS_PARAM, units)
                        .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                        .build();
                // OpenWeatherMap Query URL
                // Info at http://openweathermap.com/API#forecast
                URL url = new URL(builtUri.toString());

                // Log.v(LOG_TAG, ">>BUILD URL<< " + builtUri.toString());

                // Request to OpenWeatherMap and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a string
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // nothing to do
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                // variable to go through and read with
                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed buffer for debugging
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing
                    return null;
                }
                forecastJsonStr = buffer.toString();
            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                // If the code didn't successfully get the weather data, there's no point in attempting to parse it
                forecastJsonStr = null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }

        // Log in `adb logcat` to verify the results of the request (identical to printing to console)
        // Log.v("PROOF", forecastJsonStr);
            try {
                return getWeatherDataFromJson(forecastJsonStr, numDays);
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }
            // null is returned if the try/catch block fails
            return null;
        }

        // the following passes the results from server to main activity to populate view
        @Override
        protected void onPostExecute(String[] weatherData) {
            if (weatherData != null) {
                mForecastAdapter.clear();
                for (String dayForecastStr : weatherData) {
                    mForecastAdapter.add(dayForecastStr);
                }
            }

        }
    }
}
