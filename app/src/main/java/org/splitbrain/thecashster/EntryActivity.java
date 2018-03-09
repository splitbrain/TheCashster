package org.splitbrain.thecashster;

import android.Manifest;
import android.accounts.AccountManager;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.RectEvaluator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
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

import org.splitbrain.thecashster.Tasks.SheetsTask;
import org.splitbrain.thecashster.model.Place;
import org.splitbrain.thecashster.model.Transaction;

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
    ClearableEditText vTextSearch;
    @BindView(R.id.buttonSearch)
    Button vButtonSearch;
    @BindView(R.id.layoutNumberPad)
    View vLayoutNumberPad;
    @BindView(R.id.activityEntry)
    View vActivityEntry;
    @BindView(R.id.listPlaces)
    ListView mListView;

    /**
     * Initialize the activity
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entry);
        ButterKnife.bind(this);

        // attach adapter to our list view
        mAdapter = new PlacesAdapter(this, new ArrayList<Place>());
        mListView.setAdapter(mAdapter);
        ListViewHandler lvh = new ListViewHandler();
        mListView.setOnItemClickListener(lvh);
        mListView.setOnItemLongClickListener(lvh);

        // pull to refresh
        final SwipeRefreshLayout swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                locationUpdate();
                swipeRefresh.setRefreshing(false);
            }
        });

        // attach handlers to the search text field
        SearchTextHandler sth = new SearchTextHandler();
        vTextSearch.setOnFocusChangeListener(sth);
        vTextSearch.setOnEditorActionListener(sth);
        vTextSearch.setClearTextListener(sth);

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
        String tag = (String) v.getTag();
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
                Button b = (Button) v;
                String key = b.getText().toString();
                if ((key.equals("0") || key.equals("00")) && getAmount() == 0.0) {
                    return; // don't add zeros to a zero
                }

                if (mAmount.length() < 8) {
                    mAmount = mAmount.concat(key);
                } else {
                    Toast.makeText(getApplicationContext(), R.string.err_toomuch,
                            Toast.LENGTH_SHORT).show();
                }
                break;
        }

        updateAmountView();
    }

    /**
     * Open about activity
     */
    public void onMenuButtonPress(View v) {
        Intent intent = new Intent(this, AboutActivity.class);
        intent.putExtra("location", mLastLocation);
        startActivity(intent);
    }

    /**
     * Show Money animation
     */
    private void showAnimation() {
        final Drawable d = getResources().getDrawable(R.drawable.check);

        // center of the list view
        int cx = mListView.getMeasuredWidth() / 2;
        int cy = mListView.getMeasuredHeight() / 2;

        // start with full opacity and at the center
        d.setBounds(cx, cy, cx, cy);
        d.setAlpha(255);
        d.setTint(getResources().getColor(R.color.colorPrimaryLight));

        // add overlay
        mListView.getOverlay().add(d);

        // final size
        final int scale = 3;
        Rect r = new Rect(
                cx + (cx * -1 * scale),
                cy + (cx * -1 * scale),
                cx + (cx * scale),
                cy + (cx * scale));

        // animate
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(d,
                PropertyValuesHolder.ofObject("bounds", new RectEvaluator(), r),
                PropertyValuesHolder.ofInt("alpha", 0)
        );
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                mListView.getOverlay().remove(d);
            }

            @Override
            public void onAnimationCancel(Animator animator) {
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
            }
        });
        animator.setDuration(1000);
        animator.start();
    }

    /**
     * Store a new transaction based on the current amount and place
     */
    private void storeTransaction() {
        Place place = mAdapter.getSelected();

        // check amount and place first
        if (getAmount() == 0.0) {
            Toast.makeText(getApplicationContext(), R.string.err_noamount,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (place == null) {
            mAdapter.selectItem(0);
            if (mAdapter.getSelected() == null) {
                Toast.makeText(getApplicationContext(), R.string.err_noplace,
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), R.string.err_firstplace,
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }

        showAnimation();

        // new Transaction with attached place
        place.updateLastUsed();
        Transaction tx = new Transaction(getAmount(), place);

        // save place and transaction
        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();
        realm.copyToRealmOrUpdate(tx);
        realm.commitTransaction();
        realm.close();

        // start new task for transferring the data to Google Sheets
        startSheetsSync();

        // reset the interface
        place.setLocal(true);
        mAdapter.selectItem(-1);
        mAmount = "";
        updateAmountView();
    }

    /**
     * Start transferring the stored transactions
     */
    private void startSheetsSync() {
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
                // check if stored account name is correct
                if(mCredential.getSelectedAccountName() != null) {
                    startSheetsSync();
                    return;
                }
            }

            // Start a dialog from which the user can choose an account
            startActivityForResult(mCredential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER);
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    getString(R.string.perm_contacts),
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
                    getString(R.string.perm_location),
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
        vActivityEntry.requestFocus();
    }

    /**
     * Handlers for the ListView
     */
    private class ListViewHandler implements
            AdapterView.OnItemClickListener,
            AdapterView.OnItemLongClickListener {

        /**
         * Slect place on click
         */
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            mAdapter.selectItem(i);
            // haptic feedback
            Vibrator vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibe != null) vibe.vibrate(20);
            closeKeyboard();
        }

        /**
         * Delete local places on long press
         */
        @Override
        public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
            final Place item = mAdapter.getItem(i);
            if (item == null) return false;
            if (!item.isLocal()) return false;

            AlertDialog.Builder alert = new AlertDialog.Builder(EntryActivity.this);
            alert.setTitle(getString(R.string.delete_title, item.getName()));
            alert.setMessage(R.string.reallydel);
            alert.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Realm realm = Realm.getDefaultInstance();
                    try {
                        mAdapter.remove(item);
                        realm.beginTransaction();
                        realm.where(Place.class)
                                .equalTo("id", item.getId())
                                .findAll()
                                .deleteAllFromRealm();
                        realm.commitTransaction();
                    } catch (IllegalStateException e) {
                        Log.e(TAG, "Something went wrong when deleting the item", e);
                    }
                    realm.close();
                    dialog.dismiss();
                }
            });
            alert.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            alert.show();
            return true;
        }
    }


    /**
     * Handlers for the search field
     */
    private class SearchTextHandler implements
            View.OnFocusChangeListener,
            TextView.OnEditorActionListener,
            ClearableEditText.ClearTextListener {

        /**
         * hide numbers while searching
         */
        @Override
        public void onFocusChange(View view, boolean b) {
            if (b) {
                vLayoutNumberPad.setVisibility(View.GONE);
            } else {
                vLayoutNumberPad.setVisibility(View.VISIBLE);
            }
        }

        /**
         * search when pressing "enter" on the keyboard
         */

        @Override
        public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
            onSearchButtonClick(textView);
            return true;
        }

        /**
         * Search again when the field is cleared
         */
        @Override
        public void onTextCleared(ClearableEditText view) {
            onSearchButtonClick(view);
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
