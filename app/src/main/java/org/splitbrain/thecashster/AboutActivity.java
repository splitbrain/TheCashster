package org.splitbrain.thecashster;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.webkit.WebView;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_about);
        setSupportActionBar(toolbar);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        WebView wv = findViewById(R.id.webview_about);
        wv.loadUrl("file:///android_asset/about.html");
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
