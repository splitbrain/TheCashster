package org.splitbrain.thecashster;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Paint;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
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
        tv.setText(BuildConfig.VERSION_NAME
                + " [" + getString(R.string.app_git_hash) + "]"
                + " " + BuildConfig.VERSION_CODE
        );

        tv = findViewById(R.id.textAboutDocId);
        tv.setPaintFlags(tv.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
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

    public void onDocIDClick(View v) {
        TextView tv = (TextView) v;
        String docid = tv.getText().toString();
        if (docid.isEmpty()) return;

        String url = "https://docs.google.com/spreadsheets/d/" + docid;
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        startActivity(i);
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
