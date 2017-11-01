package org.splitbrain.thecashster;

import android.Manifest;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.sheets.v4.SheetsScopes;

import org.splitbrain.thecashster.model.Place;
import org.splitbrain.thecashster.model.Transaction;
import org.splitbrain.thecashster.sheets.SheetsTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.realm.Realm;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class EntryActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private final String TAG = this.getClass().getSimpleName();

    // preference key to store which account to use for the spreadsheet API
    private static final String PREF_ACCOUNT_NAME = "accountName";
    // requested permissions for the spreadsheet API
    private final String[] SCOPES = {SheetsScopes.SPREADSHEETS};

    // identifiers for request callbacks
    public static final int REQUEST_ACCOUNT_PICKER = 1000;
    public static final int REQUEST_AUTHORIZATION = 1001;
    public static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    public static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;
    public static final int REQUEST_FINE_LOCATION = 1004;

    // business logic
    private PlacesAdapter mAdapter;
    private String mAmount = "";
    private int mNeg = -1;
    private Location mLastLocation;

    // google API clients
    private GoogleApiClient mGoogleApiClient;
    GoogleAccountCredential mCredential;

    // views
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
        final ListView listview = findViewById(R.id.listPlaces);
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

        // Initialize Google Credential object
        mCredential = GoogleAccountCredential.usingOAuth2(
                this, Arrays.asList(SCOPES)
        ).setBackOff(new ExponentialBackOff());
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
                String key = b.getText().toString();
                if ((key.equals("0") || key.equals("00")) && getAmount() == 0.0) {
                    return; // don't add zeros to a zero
                }

                if (mAmount.length() < 8) {
                    mAmount = mAmount.concat(key);
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

        // start new task for transferring the data to Google Sheets
        startSheetsSync();

        // reset the interface
        mAdapter.selectItem(-1);
        mAmount = "";
        updateAmountView();
    }


    private void startSheetsSync() {
        // fixme check for network connectivity first

        if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
            return;
        }

        SheetsTask task = new SheetsTask(this, mCredential);
        task.execute();
    }

    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(this, Manifest.permission.GET_ACCOUNTS)) {
            // get account name from preferences
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                // account has been set, retry the sync task
                mCredential.setSelectedAccountName(accountName);
                startSheetsSync();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(mCredential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "The contacts permission is needed to authenticate your spreadsheet account",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS
            );
            // when permission was granted this method is called again, thanks to the annotation
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     *
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode  code indicating the result of the incoming
     *                    activity result.
     * @param data        Intent (containing result data) returned by incoming
     *                    activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    // save the account name to the preferences
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);

                        // try the sync task again
                        startSheetsSync();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    // try the sync task again
                    startSheetsSync();
                }
                break;
        }
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
