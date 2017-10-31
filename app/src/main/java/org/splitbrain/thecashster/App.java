package org.splitbrain.thecashster;

import android.app.Application;

import io.realm.Realm;

/**
 * The application
 *
 * @author Andreas Gohr andi@splitbrain.org
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Realm (just once per application)
        Realm.init(getApplicationContext());
    }
}
