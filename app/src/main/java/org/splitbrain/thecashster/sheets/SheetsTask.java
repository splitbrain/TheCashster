package org.splitbrain.thecashster.sheets;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.api.services.sheets.v4.model.UpdateSpreadsheetPropertiesRequest;
import com.google.api.services.sheets.v4.model.ValueRange;

import org.splitbrain.thecashster.EntryActivity;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Andreas Gohr andi@splitbrain.org
 */

public class SheetsTask extends AsyncTask<Void, Void, SheetsTask> {
    // for logging
    private final String TAG = this.getClass().getSimpleName();
    // the preference that holds our google sheets document ID
    private static final String PREF_SHEET_ID = "sheetID";
    private static final String SHEET_TITLE = "TheCashster";

    private OnTaskCompleted mListener;
    private com.google.api.services.sheets.v4.Sheets mService = null;
    private Exception mLastError = null;
    private WeakReference<EntryActivity> mContextRef;

    /**
     * Constructor
     */
    public SheetsTask(EntryActivity act, GoogleAccountCredential credential) {
        mContextRef = new WeakReference<>(act);
        HttpTransport transport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        mService = new com.google.api.services.sheets.v4.Sheets.Builder(
                transport, jsonFactory, credential)
                .setApplicationName("TheCashster")
                .build();
    }

    @Override
    protected SheetsTask doInBackground(Void... voids) {
//        Realm realm = Realm.getDefaultInstance();
//        RealmResults<Transaction> transactions = realm.where(Transaction.class).findAll();
//        for (Transaction tx : transactions) {
//            Log.i(TAG, tx.getDt().toString());
//        }

        Log.i(TAG, "Sync task started");

        try {
            //testing();
            String docId = getOrCreateDocument();
            Log.i(TAG, "DocID aquired: ".concat(docId));

            // fixme get all pending transactions, append them and then delete them locally

        } catch (IOException e) {
            mLastError = e;
            cancel(true);
        }

        return this;
    }

    /**
     * Generates the headers needed for the spreadsheet
     *
     * @return the headers
     */
    private List<List<Object>> getHeaders() {
        List<List<Object>> values = new ArrayList<>();
        List<Object> row = new ArrayList<>();

        row.add("TX ID");
        row.add("Amount");
        row.add("Date");
        row.add("Place");
        row.add("ForeignID");
        row.add("Latitude");
        row.add("Longitude");
        values.add(row);

        return values;
    }

    /**
     * Append data to the spreadsheet
     *
     * @param docId  the document ID
     * @param values two-dimensional array of cells to append
     * @throws IOException when something goes wrong
     */
    private void append(String docId, List<List<Object>> values) throws IOException {
        String range = "A1:B1";

        ValueRange requestBody = new ValueRange();
        requestBody.setValues(values);

        Sheets.Spreadsheets.Values.Append request =
                mService.spreadsheets().values().append(docId, range, requestBody).setValueInputOption("USER_ENTERED");

        AppendValuesResponse response = request.execute();
        System.out.println(response);
    }

    /**
     * Get the ID of the spreadsheet to sync to
     * <p>
     * On first time use this will create a new Document, set the title and add headers.
     * Subsequent uses will pull the document ID from the preferences and just check that the
     * doc hasn't been deleted
     *
     * @return the documentID of our spreadsheet
     * @throws IOException when something goes wrong
     */
    private String getOrCreateDocument() throws IOException {
        Context context = mContextRef.get();
        if (context == null) throw new IOException("no context available");
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // get doc from preferences and check it still exists
        String docID = preferences.getString(PREF_SHEET_ID, null);
        if (docID != null) {
            try {
                mService.spreadsheets().get(docID).execute();
                return docID;
            } catch (GoogleJsonResponseException e) {
                if (e.getStatusCode() == 404) {
                    Log.e(TAG, "Known doc is not accessible, we forget about it");
                } else {
                    throw e;
                }
            }
        }

        // create new document
        Spreadsheet doc = mService.spreadsheets().create(new Spreadsheet()).execute();

        // add title and locale
        List<Request> requests = new ArrayList<>();
        requests.add(
                new Request()
                        .setUpdateSpreadsheetProperties(
                                new UpdateSpreadsheetPropertiesRequest()
                                        .setProperties(
                                                new SpreadsheetProperties()
                                                        .setTitle(SHEET_TITLE)
                                        )
                                        .setFields("title")
                        )
        );

        requests.add(
                new Request()
                        .setUpdateSpreadsheetProperties(
                                new UpdateSpreadsheetPropertiesRequest()
                                        .setProperties(
                                                new SpreadsheetProperties()
                                                        .setLocale("en_US")
                                        )
                                        .setFields("locale")
                        )
        );
        BatchUpdateSpreadsheetRequest body =
                new BatchUpdateSpreadsheetRequest().setRequests(requests);
        mService.spreadsheets().batchUpdate(doc.getSpreadsheetId(), body).execute();

        // add header line
        append(doc.getSpreadsheetId(), getHeaders());

        // remember doc ID in preferences
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PREF_SHEET_ID, doc.getSpreadsheetId());
        editor.apply();

        Log.i(TAG, doc.getSpreadsheetId());
        return doc.getSpreadsheetId();
    }


    /**
     * Handle the cancellation of the task
     * <p>
     * This happens when any exception is thrown. Ususally we can't do anything in that
     * case and just silently ignore the error and hope it will work next time.
     * However we do handle missing authorization here by opening a dialog about it and
     * having the result passed back to our EntryActivity. Once proper auth is available,
     * the task will be restarted again.
     */
    @Override
    protected void onCancelled() {
        if (mLastError != null) {
            if (mLastError instanceof UserRecoverableAuthIOException) {
                EntryActivity context = mContextRef.get();
                if (context != null) {
                    context.startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            EntryActivity.REQUEST_AUTHORIZATION);
                }
            }
        }

        Log.e(TAG, "task was cancelled", mLastError);
    }

    // region OnTaskCompleted handling

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

    // endregion
}
