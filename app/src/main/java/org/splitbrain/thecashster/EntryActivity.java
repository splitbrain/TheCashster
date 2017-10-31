package org.splitbrain.thecashster;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import org.splitbrain.thecashster.model.Place;
import org.splitbrain.thecashster.model.Transaction;

import java.util.ArrayList;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.realm.Realm;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class EntryActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    static final int REQUEST_FINE_LOCATION = 1001;
    private PlacesAdapter mAdapter;
    private String mAmount = "";
    private int mNeg = -1;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;

    @BindView(R.id.textSearch)
    TextView vTextSearch;
    @BindView(R.id.buttonSearch)
    Button vButtonSearch;


    /**
     * Initialize the activity
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entry);
        ButterKnife.bind(this);

        // attach adapter to our list view
        final ListView listview = (ListView) findViewById(R.id.listPlaces);
        mAdapter = new PlacesAdapter(this, new ArrayList<Place>());
        listview.setAdapter(mAdapter);
        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                mAdapter.selectItem(i);
                // haptic feedback
                Vibrator vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (vibe != null) vibe.vibrate(20);
                closeKeyboard();
            }
        });

        // initialize display
        updateAmountView();

        // Create an instance of GoogleAPIClient.
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    /**
     * Handle all of the button presses for our number pad
     * <p>
     * Buttons are identified by their tag. Buttons without a tag will just add their text
     * to the current amount
     *
     * @param v the button that was pressed
     */
    public void onButtonPress(View v) {
        Button b = (Button) v;

        String tag = (String) b.getTag();
        if (tag == null) tag = "";

        // haptic feedback
        Vibrator vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibe != null) vibe.vibrate(20);

        switch (tag) {
            case "del":
                if (mAmount.length() > 0)
                    mAmount = mAmount.substring(0, mAmount.length() - 1);
                break;
            case "neg":
                mNeg *= -1;
                break;
            case "done":
                storeTransaction();
                return; // we're done
            default:
                if (mAmount.length() < 8) {
                    mAmount = mAmount.concat(b.getText().toString());
                } else {
                    Toast.makeText(getApplicationContext(), "Sorry, that's just too much",
                            Toast.LENGTH_SHORT).show();
                }
                break;
        }

        updateAmountView();
    }

    /**
     * Store a new transaction based on the current amount and place
     */
    private void storeTransaction() {
        Place place = mAdapter.getSelected();

        // check amount and place first
        if (getAmount() == 0.0) {
            Toast.makeText(getApplicationContext(), "No amount set",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (place == null) {
            mAdapter.selectItem(0);
            if (mAdapter.getSelected() == null) {
                Toast.makeText(getApplicationContext(), "No place selected",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "First place selected, click done again to confirm",
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // new Transaction with attached place
        Transaction tx = new Transaction(getAmount(), place);

        // save place and transaction
        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();
        realm.copyToRealmOrUpdate(tx);
        realm.commitTransaction();

        // FIXME start new task for transfering the data to Google Sheets

        mAmount = "";
        updateAmountView();
    }

    /**
     * Get the current amount as float
     *
     * @return the amount
     */
    private float getAmount() {
        if (mAmount.length() == 0) return 0;
        return Float.parseFloat(mAmount) / 100.0f * mNeg;
    }

    /**
     * Format the current amount nicely
     */
    private void updateAmountView() {
        float amount = getAmount();
        TextView v = this.findViewById(R.id.textAmount);
        String value = String.format(Locale.US, "%01.2f", amount);
        if (amount == 0.0 && mNeg < 0) value = "-".concat(value); // ad negative for zero
        v.setText(value);
    }

    /**
     * Update the place list
     */
    private void updatePlaceView() {
        mAdapter.clear();
        TextView tv = findViewById(R.id.textSearch);
        mAdapter.findNearbyPlaces(mLastLocation, tv.getText().toString());
    }

    /**
     * Fetch the current location and trigger a place update
     *
     * @todo see https://stackoverflow.com/a/46482065/172068 about the deprecated API
     */
    @SuppressWarnings("deprecation")
    @SuppressLint("MissingPermission")
    @AfterPermissionGranted(REQUEST_FINE_LOCATION)
    public void locationUpdate() {
        if (!EasyPermissions.hasPermissions(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            EasyPermissions.requestPermissions(
                    this,
                    "Location access is needed to find nearby places",
                    REQUEST_FINE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        }
        updatePlaceView();
    }

    /**
     * Handle click on the search button
     */
    public void onSearchButtonClick(View v) {
        closeKeyboard();
        mAdapter.clear();
        mAdapter.findNearbyPlaces(mLastLocation, vTextSearch.getText().toString());
    }

    /**
     * Close the keyboard
     */
    private void closeKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(vTextSearch.getWindowToken(), 0);
        }
    }


    // region Interface handlers

    @Override
    protected void onStart() {
        // Connect to Google Play Services
        mGoogleApiClient.connect();
        super.onStart();
    }

    @Override
    protected void onStop() {
        // Disconnect to Google Play Services
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        locationUpdate();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    // endregion
}
