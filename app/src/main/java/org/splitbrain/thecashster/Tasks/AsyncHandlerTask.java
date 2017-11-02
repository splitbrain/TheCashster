package org.splitbrain.thecashster.Tasks;

import android.os.AsyncTask;

/**
 * This AsyncTask returns itself and notifies a set handler
 *
 * @author Andreas Gohr andi@splitbrain.org
 */
abstract public class AsyncHandlerTask<Params, Progress> extends AsyncTask<Params, Progress, AsyncHandlerTask> {

    private OnTaskCompleted mListener;

    /**
     * Interface for TaskCompleted Listener
     */
    public interface OnTaskCompleted {
        void onTaskCompleted(AsyncHandlerTask task);
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
    protected void onPostExecute(AsyncHandlerTask task) {
        if (mListener != null) {
            mListener.onTaskCompleted(task);
        }
    }
}
