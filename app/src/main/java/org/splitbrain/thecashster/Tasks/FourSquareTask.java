package org.splitbrain.thecashster.Tasks;

import android.net.Uri;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.splitbrain.thecashster.model.Place;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

/**
 * @author Andreas Gohr andi@splitbrain.org
 */

public class FourSquareTask extends AsyncHandlerTask<Void, Void> {

    private final String TAG = this.getClass().getSimpleName();

    private Exception mLastError = null;
    private URL mURL;
    private ArrayList<Place> mPlaces;
    private LatLng mLocation;

    /**
     * Ask Foursquare for nearby places
     *
     * @param clientId Foursquare API client ID
     * @param secret Foursquare API client secret
     * @param ll The current location
     * @param radius The search radius to use
     * @param filter The wanted text filter
     */
    public FourSquareTask(String clientId, String secret, LatLng ll, int radius, String filter) {
        mPlaces = new ArrayList<>();
        mLocation = ll;

        Uri uri = new Uri.Builder()
                .scheme("https")
                .authority("api.foursquare.com")
                .appendPath("v2")
                .appendPath("venues")
                .appendPath("search")
                .appendQueryParameter("client_id", clientId)
                .appendQueryParameter("client_secret", secret)
                .appendQueryParameter("v", "20171101")
                .appendQueryParameter("ll", ll.latitude + "," + ll.longitude)
                .appendQueryParameter("query", filter)
                .appendQueryParameter("limit", "25")
                .appendQueryParameter("radius", String.valueOf(radius))
                .build();

        try {
            mURL = new URL(uri.toString());
        } catch (MalformedURLException e) {
            // should not happen
            Log.e(TAG, "We failed to create a proper URL", e);
        }
    }

    @Override
    protected FourSquareTask doInBackground(Void... voids) {
        try {
            String data = fetchData();
            parseData(data);
        } catch (Exception e) {
            mLastError = e;
            cancel(true);
        }

        return this;
    }

    /**
     * Access the foursquare API and get the response body
     */
    private String fetchData() throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        HttpURLConnection urlConnection = (HttpURLConnection) mURL.openConnection();
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
            bufferedReader.close();
            return stringBuilder.toString();
        } finally {
            urlConnection.disconnect();
        }
    }

    /**
     * Create the list of places from the response
     */
    private void parseData(String data) throws JSONException {
        JSONObject json = (JSONObject) new JSONTokener(data).nextValue();

        JSONArray venues = json.getJSONObject("response").getJSONArray("venues");
        for (int i = 0; i < venues.length(); i++) {
            Place place = new Place();
            JSONObject venue = venues.getJSONObject(i);
            JSONObject loc = venue.getJSONObject("location");
            JSONArray cat = venue.getJSONArray("categories");

            place.setFoursquare(venue.getString("id"));
            place.setName(venue.getString("name"));
            if (loc.has("address"))
                place.setAddress(loc.getString("address"));
            if (cat.length() > 0 && cat.getJSONObject(0).has("shortName"))
                place.setCategory(cat.getJSONObject(0).getString("shortName"));
            place.setLat(loc.getDouble("lat"));
            place.setLon(loc.getDouble("lng"));
            place.setDistanceFrom(mLocation);

            mPlaces.add(place);
        }

    }

    /**
     * Getter to access the places once the task complete
     */
    public ArrayList<Place> getPlaces() {
        return mPlaces;
    }

    @Override
    protected void onCancelled() {

        if (mLastError != null) {
            Log.e(TAG, "The Task was cancelled because of an error", mLastError);
        } else {
            Log.e(TAG, "The Task was cacelled");
        }
    }
}
