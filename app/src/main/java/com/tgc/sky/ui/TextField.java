package com.tgc.sky.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.RelativeLayout;

import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.view.ViewCompat;

import com.tgc.sky.GameActivity;
import com.tgc.sky.SystemUI_android;
import com.tgc.sky.util.IdCounter;

import java.nio.charset.StandardCharsets;

public class TextField {
    public static final int kInvalidTextFieldId = -1;

    private GameActivity m_activity;
    private int m_imeOptions;
    private int m_inputType;
    private boolean m_isCallbackTextfield;
    private EditText m_textField;
    private SystemUI_android m_systemUI;
    private TextFieldLimiter m_textFieldLimiter;
    private final Rect m_hitRect = new Rect();
    private final IdCounter<Integer> m_idCounter = new IdCounter<>(-1);

    private String m_textBuffer = "";
    private int m_cursorPos = -1;
    private int m_selectPos = -1;
    private String m_textBufferProgram = "";
    private int m_cursorPosProgram = -1;
    private int m_selectPosProgram = -1;

    private boolean m_submitted = false;
    private boolean m_hiding = false;
    private State m_state = State.kTextFieldState_Hidden;
    private boolean m_init = false;

    private static String sChatDraft = "";
    private boolean m_isDraftEnabled = false;

    public enum State {
        kTextFieldState_Hidden,
        kTextFieldState_RequestHide,
        kTextFieldState_RequestShow,
        kTextFieldState_Showing
    }

    public void initWithParams(GameActivity gameActivity) {
        this.m_activity = gameActivity;

        AppCompatEditText appCompatEditText = new AppCompatEditText(this.m_activity) {

            @Override
            public boolean onKeyPreIme(int code, KeyEvent event) {
                if (code == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    InputMethodManager imm = (InputMethodManager) TextField.this.m_activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(TextField.this.m_textField.getWindowToken(), 0);
                    TextField.this.m_textField.clearFocus();
                    return false;
                }
                return super.onKeyPreIme(code, event);
            }

            @Override
            public boolean onKeyDown(int code, KeyEvent event) {
                if (code == KeyEvent.KEYCODE_ENTER) {
                    return true;
                }
                return super.onKeyDown(code, event);
            }

            private void updateCursorPos(int selStart, int selLength, String str) {
                int caret = selStart + selLength;
                TextField.this.m_cursorPos = TextField.this.updateCursorPosUTF8(caret, str);
                TextField.this.m_selectPos = TextField.this.updateCursorPosUTF8(selStart, str);
            }
        };

        this.m_textField = appCompatEditText;
        this.m_inputType = 49153;
        this.m_imeOptions = 33554436;

        appCompatEditText.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable s) {}
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!TextField.this.m_init || TextField.this.m_textField.getVisibility() == View.INVISIBLE) return;
                TextField.this.updateBuffer(TextField.this.m_textField.getText().toString());
                if (TextField.this.m_isDraftEnabled) {
                    TextField.sChatDraft = TextField.this.m_textField.getText().toString();
                }
            }
        });

        this.m_textField.setTextColor(ViewCompat.MEASURED_STATE_MASK);
        this.m_textField.setTextSize(18.0f);
        this.m_textField.setFitsSystemWindows(true);

        this.m_textFieldLimiter = new TextFieldLimiter();
        resizeTextField(true, 0);

        GradientDrawable bg = new GradientDrawable();
        this.m_textField.setBackground(bg);
        bg.setCornerRadius(Utils.dp2px(8.0f));
        bg.setColor(Color.argb(128, 255, 255, 255));
        bg.setStroke(1, Color.argb(204, 255, 255, 255));

        this.m_textField.setImeOptions(33554432);
        this.m_textField.setHintTextColor(0xFF444444);
        this.m_textField.setGravity(8388627);
        this.m_textField.setInputType(16385);
        this.m_textField.setVisibility(View.INVISIBLE);
        this.m_textField.setFocusable(true);
        this.m_textField.setFocusableInTouchMode(true);

        this.m_textField.setOnEditorActionListener((textView, imeAction, keyEvent) -> {
            if (imeAction == EditorInfo.IME_ACTION_SEND || imeAction == EditorInfo.IME_ACTION_UNSPECIFIED) {
                TextField.this.m_activity.onKeyboardCompleteNative(
                        textView.getText().toString(),
                        TextField.this.m_isCallbackTextfield,
                        TextField.this.m_isCallbackTextfield
                );
                git.artdeell.skymodloader.MainActivity.onKeyboardCompleteNative(textView.getText().toString());
                textView.setText("");
                if (TextField.this.m_isDraftEnabled) {
                    TextField.sChatDraft = "";
                }
                TextField.this.m_submitted = true;
                return true;
            }
            return false;
        });

        this.m_textField.setFilters(new InputFilter[]{this.m_textFieldLimiter});
        this.m_activity.getBridgeView().addView(this.m_textField);

        this.m_activity.addOnKeyboardListener((visible, kbHeight) -> {
            TextField.this.resizeTextField(false, kbHeight);
            if (!visible && TextField.this.isVirtualKeyboard()
                    && TextField.this.getState() == State.kTextFieldState_Showing) {
                TextField.this.hideTextField();
            }
        });

        this.m_init = true;
    }

    private boolean isVirtualKeyboard() {
        return this.m_activity.getResources().getConfiguration().keyboard == 1;
    }

    private void showVirtualKeyboard() {
        if (isVirtualKeyboard()) {
            ((InputMethodManager) this.m_activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(this.m_textField, 0);
        }
    }

    public int showTextFieldWithPromptAsync(final String prompt, final String initial, final int maxChars, final int maxBytes, final boolean isCallback) {
        int id;
        if (getState() != State.kTextFieldState_Hidden || (id = tryActivate()) == -1) {
            return kInvalidTextFieldId;
        }
        setState(State.kTextFieldState_RequestShow);
        this.m_activity.runOnUiThread(() -> showTextFieldWithPrompt(prompt, initial, maxChars, maxBytes, isCallback));
        return id;
    }

    public void showTextFieldWithPrompt(String prompt, String initial, int maxChars, int maxBytes, boolean isCallback) {
        initBufferAndCursorPos(initial);
        this.m_isCallbackTextfield = isCallback;
        this.m_submitted = false;
        this.m_textFieldLimiter.maxByteSize = maxBytes;
        this.m_textFieldLimiter.maxCharacters = maxChars;

        this.m_isDraftEnabled = ((this.m_imeOptions & EditorInfo.IME_MASK_ACTION) == EditorInfo.IME_ACTION_SEND);
        String __initial = (m_isDraftEnabled && (initial == null || initial.isEmpty())) ? sChatDraft : initial;

        this.m_textField.setText(__initial);
        EditText et = this.m_textField;
        et.setSelection(0, et.getText().length());
        this.m_textField.setHint(prompt);
        this.m_textField.setAlpha(0.0f);
        this.m_textField.setInputType(this.m_inputType);
        this.m_textField.setImeOptions(this.m_imeOptions);
        this.m_textField.setVisibility(View.VISIBLE);
        this.m_textField.setEnabled(true);
        this.m_textField.setFocusable(true);
        this.m_textField.setFocusableInTouchMode(true);
        this.m_textField.requestFocus();

        resizeTextField(false, 0);
        showVirtualKeyboard();
        setState(State.kTextFieldState_Showing);
    }

    public void hideTextFieldAsync() {
        if (getState() == State.kTextFieldState_RequestHide || getState() == State.kTextFieldState_Hidden) {
            return;
        }
        setState(State.kTextFieldState_RequestHide);
        this.m_activity.runOnUiThread(this::hideTextField);
    }

    public void hideTextField() {
        if (getState() == State.kTextFieldState_Hidden || this.m_hiding) {
            return;
        }
        this.m_hiding = true;
        try {
            if (!this.m_submitted) {
                if (this.m_isDraftEnabled) {
                    sChatDraft = this.m_textField.getText().toString();
                }
                this.m_activity.onKeyboardCompleteNative("", this.m_isCallbackTextfield, true);
            }
            ((InputMethodManager) this.m_activity.getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(this.m_textField.getWindowToken(), 0);
            GameActivity.hideNavigationFullScreen(this.m_activity.getBridgeView());
            this.m_textField.setInputType(0);
            this.m_textField.setImeOptions(0);
            this.m_textField.clearFocus();
            this.m_textField.setFocusableInTouchMode(false);
            this.m_textField.setFocusable(false);
            this.m_textField.setEnabled(false);
            this.m_textField.setVisibility(View.GONE);
            this.m_activity.getBridgeView().requestFocus();
            this.m_activity.getBridgeView().requestFocusFromTouch();
            clearId();
            setState(State.kTextFieldState_Hidden);
        } finally {
            this.m_hiding = false;
        }
    }

    public void resizeTextField(boolean top, int keyboardHeight) {
        int pad = Utils.dp2px(8.0f);
        Rect insets = this.m_activity.GetSafeAreaInsets();

        RelativeLayout.LayoutParams lp =
                new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);

        if (top) {
            lp.addRule(RelativeLayout.ALIGN_PARENT_TOP, -1);
            lp.topMargin = insets.top + pad;
            lp.bottomMargin = pad;
        } else {
            lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, -1);
            lp.bottomMargin = keyboardHeight + pad;
        }
        lp.leftMargin = insets.left + pad;
        lp.rightMargin = insets.right + pad;

        this.m_textField.setLayoutParams(lp);
        this.m_textField.setPadding(pad, pad, pad, pad);
        this.m_textField.setFitsSystemWindows(true);
        this.m_textField.setAlpha(1.0f);
    }

    public String getTextBuffer() {
        return this.m_textBuffer;
    }

    public String getPrompt() {
        CharSequence hint = this.m_textField.getHint();
        return hint == null ? "" : hint.toString();
    }

    public int getCursorPos() {
        return this.m_cursorPos;
    }

    public int getSelectPos() {
        return this.m_selectPos;
    }

    public float getTextFieldHeight() {
        return this.m_textField.getHeight();
    }

    public Rect getHitRect() {
        this.m_textField.getHitRect(this.m_hitRect);
        return this.m_hitRect;
    }

    private int updateCursorPosUTF8(int i, String str) {
        return str.substring(0, i).getBytes(StandardCharsets.UTF_8).length;
    }

    private void updateBuffer(String str) {
        this.m_textBuffer = str;
    }

    private void initBufferAndCursorPos(String initial) {
        this.m_textBuffer = initial == null ? "" : initial;
        this.m_cursorPos = -1;
        this.m_selectPos = -1;
        this.m_cursorPosProgram = -1;
        this.m_selectPosProgram = -1;
    }

    public void setTextBufferAndCursorPosProgram(String str) {
        updateBuffer(str);
        this.m_textBufferProgram = this.m_textBuffer;
        int length = str.length();
        this.m_cursorPos = updateCursorPosUTF8(length, str);
        this.m_selectPos = updateCursorPosUTF8(length, str);
        this.m_cursorPosProgram = this.m_cursorPos;
        this.m_selectPosProgram = this.m_selectPos;
    }

    public int tryActivate() {
        int id;
        synchronized (this) {
            id = this.m_idCounter.TryActivate().intValue();
        }
        return id;
    }

    public boolean isIdActive(int i) {
        boolean active;
        synchronized (this) {
            active = this.m_idCounter.IsActiveId(Integer.valueOf(i));
        }
        return active;
    }

    public void clearId() {
        synchronized (this) {
            this.m_idCounter.ClearId();
        }
    }

    public State getState() {
        return this.m_state;
    }

    public void setState(State state) {
        this.m_state = state;
    }

    private static class TextFieldLimiter implements InputFilter {
        public int maxCharacters = Integer.MAX_VALUE;
        public int maxByteSize = Integer.MAX_VALUE;

        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                                   android.text.Spanned dest, int dstart, int dend) {
            return null;
        }
    }

    private static class Utils {
        static int dp2px(float dp) {
            float density = Resources.getSystem().getDisplayMetrics().density;
            return (int) (dp * density + 0.5f);
        }
    }
}
