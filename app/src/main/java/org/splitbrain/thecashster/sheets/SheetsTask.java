package org.splitbrain.thecashster.sheets;

import android.os.AsyncTask;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;

/**
 * @author Andreas Gohr andi@splitbrain.org
 */

abstract class SheetsTask extends AsyncTask<Void, Void, SheetsTask> {

    private OnTaskCompleted mListener;

    private com.google.api.services.sheets.v4.Sheets mService = null;
    private Exception mLastError = null;

    /**
     * Constructor
     * 
     * @param credential
     */
    SheetsTask(GoogleAccountCredential credential) {
        HttpTransport transport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        mService = new com.google.api.services.sheets.v4.Sheets.Builder(
                transport, jsonFactory, credential)
                .setApplicationName("TheCashster")
                .build();
    }

    /**
     * Interface for TaskCompleted mListener
     */
    public interface OnTaskCompleted {
        void onTaskCompleted(SheetsTask task);
    }

    /**
     * Attach callback to be notified when task completed
     */
    public void setOnTaskCompleted(OnTaskCompleted listener) {
        mListener = listener;
    }

    /**
     * Call mListener
     *
     * @param task this class
     */
    protected void onPostExecute(SheetsTask task) {
        if (mListener != null) {
            mListener.onTaskCompleted(task);
        }
    }

}
