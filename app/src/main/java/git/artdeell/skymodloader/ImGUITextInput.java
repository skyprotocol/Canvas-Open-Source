package git.artdeell.skymodloader;

import static android.content.Context.INPUT_METHOD_SERVICE;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * This class is intended for sending characters used in chat via the virtual keyboard
 */
public class ImGUITextInput extends androidx.appcompat.widget.AppCompatEditText {
    private static final String TEXT_FILLER = "\u2800";
    private static final int INPUT_TYPE_WITHOUT_AUTOCORRECT = InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
    private static final int INPUT_TYPE_WITH_AUTOCORRECT = InputType.TYPE_CLASS_TEXT
            | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE;

    /** To disable keyboard autocorrect, change this assignment to INPUT_TYPE_WITHOUT_AUTOCORRECT. */
    private static final int INPUT_TYPE = INPUT_TYPE_WITH_AUTOCORRECT;

    public ImGUITextInput(@NonNull Context context) {
        this(context, null);
    }
    public ImGUITextInput(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, android.R.attr.editTextStyle);
    }
    public ImGUITextInput(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        imm = (InputMethodManager) getContext().getSystemService(INPUT_METHOD_SERVICE);
        setup();
    }
    private final InputMethodManager imm;

    /**
     * When we change from app to app, the keyboard gets disabled.
     * So, we disable the object
     */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        disable();
    }

    /**
     * Intercepts the back key to disable focus
     * Does not affect the rest of the activity.
     */
    @Override
    public boolean onKeyPreIme(final int keyCode, final KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            disable();
        }
        return super.onKeyPreIme(keyCode, event);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection inputConnection = super.onCreateInputConnection(outAttrs);
        outAttrs.inputType = INPUT_TYPE;
        outAttrs.imeOptions |= EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        if (inputConnection == null) {
            return null;
        }
        return new InputConnectionWrapper(inputConnection, false) {
            @Override
            public boolean setComposingText(CharSequence text, int newCursorPosition) {
                String before = getInputText();
                boolean result = super.setComposingText(text, newCursorPosition);
                submitTextDiff(before, getInputText());
                ensureFiller();
                return result;
            }

            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                String before = getInputText();
                boolean result = super.commitText(text, newCursorPosition);
                submitTextDiff(before, getInputText());
                ensureFiller();
                return result;
            }

            @Override
            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                return deleteTextAroundCursor(
                        () -> super.deleteSurroundingText(beforeLength, afterLength),
                        beforeLength,
                        afterLength
                );
            }

            @Override
            public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
                return deleteTextAroundCursor(
                        () -> super.deleteSurroundingTextInCodePoints(beforeLength, afterLength),
                        beforeLength,
                        afterLength
                );
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                String before = getInputText();
                boolean result = super.sendKeyEvent(event);
                String after = getInputText();

                if (!before.equals(after)) {
                    submitTextDiff(before, after);
                    ensureFiller();
                    return result;
                }

                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
                        submitBackspace(1);
                        ensureFiller();
                        return true;
                    }
                    if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                        sendEnter();
                        return true;
                    }

                    int unicodeChar = event.getUnicodeChar();
                    if (unicodeChar > 0) {
                        String text = Character.toString((char) unicodeChar);
                        submitText(text, 0);
                        appendInputText(text);
                        return true;
                    }
                }

                ensureFiller();
                return result;
            }
        };
    }

    /**
     * Set the soft keyboard state
     * @param state the state (true=visible)
     */
    public void setKeyboardState(boolean state) {
        if(state) {
            enable();
            requestFocus();
            imm.showSoftInput(this, InputMethodManager.SHOW_FORCED);
        } else {
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
            clear();
            disable();
        }
    }


    /**
     * Clear the EditText from any leftover inputs
     * It does not affect the in-game input
     */
    public void clear(){
        Editable editable = getEditableText();
        editable.clear();
        editable.append(TEXT_FILLER);
        Selection.setSelection(editable, editable.length());
    }

    private String withoutFiller(CharSequence text) {
        StringBuilder builder = new StringBuilder(text.length());
        char filler = TEXT_FILLER.charAt(0);
        for (int i = 0; i < text.length(); ++i) {
            char ch = text.charAt(i);
            if (ch != filler) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private String getInputText() {
        return withoutFiller(getEditableText());
    }

    private void ensureFiller() {
        Editable editable = getEditableText();
        if (editable.length() == 0) {
            editable.append(TEXT_FILLER);
        }
        Selection.setSelection(editable, editable.length());
    }

    private void submitText(String text, int start) {
        for (int i = start; i < text.length(); ++i) {
            ImGUI.submitUnicodeEvent(text.charAt(i));
        }
    }

    private void submitBackspace(int count) {
        for (int i = 0; i < count; ++i) {
            ImGUI.submitKeyEvent(KeyEvent.KEYCODE_DEL, true);
            ImGUI.submitKeyEvent(KeyEvent.KEYCODE_DEL, false);
        }
    }

    private void submitTextDiff(String oldText, String newText) {
        if (oldText.equals(newText)) {
            return;
        }

        int commonPrefix = 0;
        int maxCommonPrefix = Math.min(oldText.length(), newText.length());
        while (commonPrefix < maxCommonPrefix
                && oldText.charAt(commonPrefix) == newText.charAt(commonPrefix)) {
            commonPrefix++;
        }

        submitBackspace(oldText.length() - commonPrefix);
        submitText(newText, commonPrefix);
    }

    private void appendInputText(String text) {
        Editable editable = getEditableText();
        editable.append(text);
        Selection.setSelection(editable, editable.length());
    }

    private boolean deleteTextAroundCursor(InputConnectionAction action, int beforeLength, int afterLength) {
        String before = getInputText();
        boolean result = action.run();
        String after = getInputText();

        if (before.equals(after) && beforeLength > 0 && afterLength == 0) {
            submitBackspace(beforeLength);
        } else {
            submitTextDiff(before, after);
        }

        ensureFiller();
        return result;
    }

    private interface InputConnectionAction {
        boolean run();
    }

    /** Regain ability to exist, take focus and have some text being input */
    public void enable(){
        setEnabled(true);
        setFocusable(true);
        setVisibility(VISIBLE);
        requestFocus();

    }

    /** Lose ability to exist, take focus and have some text being input */
    public void disable(){
        clear();
        setVisibility(GONE);
        clearFocus();
        setEnabled(false);
        // End the ImGui editing session in lockstep.  Hiding this view alone
        // leaves the InputText active and io.WantTextInput stuck true, which
        // desynchronises GameActivity's imguiKeybaordShowing latch: it believes
        // the keyboard is already up and never re-shows it, so tapping a text
        // field afterwards only moves the caret.
        // Placed here rather than at the individual dismissal sites so the view
        // state and the ImGui session cannot diverge - every route that hides
        // the IME (BACK key, window focus change, IME "done" on a multiline
        // field, GameActivity lowering the keyboard) passes through here.
        // Safe to call unconditionally: the native side only acts when the
        // ImGui item being edited is the one holding ActiveId, so this is a
        // no-op when ImGui has already deactivated the field or is busy with
        // some other widget.
        ImGUI.clearTextFocus();
    }

    /** Send the enter key. */
    private void sendEnter(){
        ImGUI.submitKeyEvent(KeyEvent.KEYCODE_ENTER, true);
        ImGUI.submitKeyEvent(KeyEvent.KEYCODE_ENTER, false);
        clear();
    }

    /** This function deals with anything that has to be executed when the constructor is called */
    private void setup(){
        setRawInputType(INPUT_TYPE);
        setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        setOnEditorActionListener((textView, i, keyEvent) -> {
            sendEnter();
            clear();
            disable();
            return false;
        });
        clear();
        disable();
    }
}
