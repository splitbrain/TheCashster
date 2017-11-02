package org.splitbrain.thecashster;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.MotionEvent;

/**
 * An EditText variant with a clear button
 *
 * The icon can be changed by creating a drawable with the fixed resource name
 * "clearable_edit_text_icon"
 *
 * @link https://gist.github.com/andretietz/86cff1a170c3e7da887289a27ec0a20b
 */
public class ClearableEditText extends android.support.v7.widget.AppCompatEditText {

    private static final int DEFAULT_CLEAR_ICON = android.R.drawable.ic_menu_close_clear_cancel;
    private final Rect bounds = new Rect();
    private Drawable clearDrawable;
    private boolean showClearButton = false;
    private ClearTextListener clearTextListener;

    public ClearableEditText(Context context) {
        super(context);
        init(context, null, 0);
    }

    public ClearableEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public ClearableEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    public void setClearTextListener(ClearTextListener listener) {
        this.clearTextListener = listener;
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {

        int resourceId = context.getResources().getIdentifier(
                "clearable_edit_text_icon", "drawable", context.getPackageName());
        if (resourceId == 0) resourceId = DEFAULT_CLEAR_ICON;

        clearDrawable = ContextCompat.getDrawable(context, resourceId);
        setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight() + clearDrawable.getIntrinsicWidth(),
                getPaddingBottom());

        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                showClearButton = !charSequence.toString().isEmpty();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.getClipBounds(bounds);
        if (showClearButton) {
            bounds.left = bounds.right - clearDrawable.getIntrinsicWidth();
            bounds.top = ((bounds.bottom - clearDrawable.getIntrinsicHeight()) / 2);
            bounds.bottom = bounds.top + clearDrawable.getIntrinsicHeight();
            clearDrawable.setBounds(bounds);
            clearDrawable.draw(canvas);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (showClearButton) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            int left = getWidth() - getPaddingRight() - clearDrawable.getIntrinsicWidth();
            int right = getWidth();
            boolean tappedX = x >= left && x <= right && y >= 0 && y <= (getBottom() - getTop());
            if (tappedX) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    setText("");
                    showClearButton = false;
                    requestFocus();
                    if (clearTextListener != null) {
                        clearTextListener.onTextCleared();
                    }
                }
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    public interface ClearTextListener {
        void onTextCleared();
    }
}
