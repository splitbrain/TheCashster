package org.splitbrain.thecashster;

import android.annotation.SuppressLint;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.webkit.WebView;
import android.widget.TextView;

import org.splitbrain.thecashster.Tasks.SheetsTask;
import org.splitbrain.thecashster.model.Place;
import org.splitbrain.thecashster.model.Transaction;

import io.realm.Realm;

/**
 * Display a README and some debug output
 */
public class AboutActivity extends AppCompatActivity {

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Toolbar toolbar = findViewById(R.id.toolbar_about);
        setSupportActionBar(toolbar);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        WebView wv = findViewById(R.id.webview_about);
        wv.loadUrl("file:///android_asset/README.html");

        TextView tv;

        tv = findViewById(R.id.textAboutVersion);
        tv.setText(BuildConfig.VERSION_NAME + " [" + getString(R.string.app_git_hash) + "]");

        tv = findViewById(R.id.textAboutDocId);
        tv.setText(PreferenceManager.getDefaultSharedPreferences(this)
                .getString(SheetsTask.PREF_SHEET_ID, "<none>"));

        Realm realm = Realm.getDefaultInstance();

        tv = findViewById(R.id.textAboutNumberPlaces);
        tv.setText(String.valueOf(realm.where(Place.class).count()));

        tv = findViewById(R.id.textAboutPendingTx);
        tv.setText(String.valueOf(realm.where(Transaction.class).count()));

        realm.close();

        Location ll = getIntent().getParcelableExtra("location");
        tv = findViewById(R.id.textAboutLocation);
        tv.setText(ll.getLatitude() + "," + ll.getLongitude() + " (±" + ll.getAccuracy() + "m)");
    }


    /**
     * finish the activity when the up button it pressed
     *
     * @link https://stackoverflow.com/a/47063857/172068
     */
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
