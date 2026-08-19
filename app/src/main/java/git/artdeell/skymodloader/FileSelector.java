package git.artdeell.skymodloader;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;

import com.tgc.sky.GameActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class FileSelector implements GameActivity.OnActivityResultListener {
    private static final int SELECTING_FILE = 1214;
    private static final FileSelector selector = new FileSelector();
    private GameActivity gameActivity;
    private long callbackFunction = 0;
    private boolean isSaveMode;
    private boolean isMultiFilesMode;
    public static void setActivity(GameActivity activity) {
        activity.AddOnActivityResultListener(selector);
        selector.gameActivity = activity;
    }
    public static void unsetActivity() {
        selector.gameActivity = null;
    }

    public static boolean nselectFile(String mimeType, long callbackFunction, boolean save) {
        return selector.selectFile(mimeType, callbackFunction, save);
    }
    public boolean selectFile(String mimeType, long callbackFunction, boolean save) {
        return selectFileMulti(new String[]{ mimeType }, callbackFunction, save);
    }

    public static boolean nselectFileMulti(String[] mimeTypes, long callbackFunction, boolean save) {
        return selector.selectFileMulti(mimeTypes, callbackFunction, save);
    }

    // Save mode with a proposed file name.
    //
    // Kept as a SEPARATE entry point rather than a fourth argument on the one
    // above: nselectFileMulti is resolved from native by its exact signature
    // ([Ljava/lang/String;JZ)Z, so every mod built against the old Canvas would
    // fail to find it the moment that changed.
    public static boolean nselectFileMultiNamed(String[] mimeTypes, long callbackFunction,
                                                boolean save, String suggestedName) {
        return selector.selectFileMulti(mimeTypes, callbackFunction, save, suggestedName);
    }

    public boolean selectFileMulti(String[] mimeTypes, long callbackFunction, boolean save) {
        return selectFileMulti(mimeTypes, callbackFunction, save, null);
    }

    // suggestedName - initial name for the save picker, or null to leave the
    //   field empty as before. May include the extension; see buildPickerIntent.
    public boolean selectFileMulti(String[] mimeTypes, long callbackFunction, boolean save,
                                   String suggestedName) {
        if(this.callbackFunction != 0) return false;
        if(mimeTypes == null || mimeTypes.length == 0) return false;

        // Copy so caller mutations don't race the UI-thread Intent build.
        final String[] types = Arrays.copyOf(mimeTypes, mimeTypes.length);

        // ACTION_CREATE_DOCUMENT doesn't support EXTRA_MIME_TYPES; only types[0] is used.
        if(save && types.length > 1) {
            Log.w("fileselector",
                  "save mode received " + types.length
                  + " MIME types; using first ('" + types[0] + "') only");
        }

        gameActivity.runOnUiThread(()->{
            this.callbackFunction = callbackFunction;
            this.isSaveMode = save;
            this.isMultiFilesMode = false;
            gameActivity.startActivityForResult(
                buildPickerIntent(types, save, /*allowMultiple=*/false, suggestedName),
                SELECTING_FILE);
        });
        return true;
    }

    public static boolean nselectFiles(String[] mimeTypes, long callbackFunction) {
        return selector.selectFiles(mimeTypes, callbackFunction);
    }
    public boolean selectFiles(String[] mimeTypes, long callbackFunction) {
        if(this.callbackFunction != 0) return false;
        if(mimeTypes == null || mimeTypes.length == 0) return false;

        final String[] types = Arrays.copyOf(mimeTypes, mimeTypes.length);

        gameActivity.runOnUiThread(()->{
            this.callbackFunction = callbackFunction;
            this.isSaveMode = false;
            this.isMultiFilesMode = true;
            gameActivity.startActivityForResult(
                buildPickerIntent(types, /*save=*/false, /*allowMultiple=*/true,
                                  /*suggestedName=*/null),
                SELECTING_FILE);
        });
        return true;
    }

    private static Intent buildPickerIntent(String[] types, boolean save, boolean allowMultiple,
                                            String suggestedName) {
        Intent i = new Intent(save ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        if(allowMultiple) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        if(save || types.length == 1) {
            i.setType(types[0]);
        } else {
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES, types);
        }
        // ACTION_CREATE_DOCUMENT reads its initial file name from EXTRA_TITLE;
        // the user can still change it before saving. Without it the name field
        // opens empty, and an empty display name is NOT left empty by the
        // platform - FileUtils.buildValidFatFilename substitutes the literal
        // "(invalid)", so saved files arrive as "(invalid).json".
        //
        // The name may carry its extension. FileUtils.splitFileName keeps the
        // caller's extension when it maps back to the requested MIME type and
        // replaces it otherwise, so "sheet.json" + "application/json" stays
        // "sheet.json" rather than gaining a second suffix. Illegal characters
        // and over-long names are sanitized by the provider, so nothing here has
        // to - only the empty case needs guarding, since that is the one
        // buildValidFatFilename cannot rescue.
        if(save && suggestedName != null && !suggestedName.isEmpty()) {
            i.putExtra(Intent.EXTRA_TITLE, suggestedName);
        }
        return i;
    }

    @Override
    public void onActivityResult(int i, int i2, Intent intent) {
        if(i != SELECTING_FILE) return;

        final long fCallbackFunction = callbackFunction;
        final boolean wasMulti = isMultiFilesMode;
        callbackFunction = 0;
        isMultiFilesMode = false;

        if(wasMulti) {
            final int[] fds = (i2 == Activity.RESULT_OK) ? collectFds(intent) : new int[0];
            new Thread(()->{
                multiCallbackFunctionCall(fCallbackFunction, fds);
            }).start();
            return;
        }

        int fd = -1;
        if(i2 == Activity.RESULT_OK) {
            Uri selectedFile = intent.getData();
            try {
                ParcelFileDescriptor fileDescriptor = gameActivity.getContentResolver().openFileDescriptor(selectedFile, isSaveMode ? "w" : "r");
                fd = fileDescriptor.detachFd();
                fileDescriptor.close();
            }catch (Exception e) {
                e.printStackTrace();
            }
        }
        final int fFd = fd;
        new Thread(()->{
            callbackFunctionCall(fCallbackFunction, fFd);
        }).start();
    }

    private int[] collectFds(Intent intent) {
        List<Uri> uris = new ArrayList<>();
        ClipData clip = intent.getClipData();
        if(clip != null) {
            for(int k = 0; k < clip.getItemCount(); ++k) {
                uris.add(clip.getItemAt(k).getUri());
            }
        } else {
            Uri single = intent.getData();
            if(single != null) uris.add(single);
        }

        List<Integer> opened = new ArrayList<>(uris.size());
        for(Uri u : uris) {
            try {
                ParcelFileDescriptor pfd = gameActivity.getContentResolver().openFileDescriptor(u, "r");
                opened.add(pfd.detachFd());
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        int[] out = new int[opened.size()];
        for(int k = 0; k < opened.size(); ++k) out[k] = opened.get(k);
        return out;
    }

    public static native void callbackFunctionCall(long cb, int fd);
    public static native void multiCallbackFunctionCall(long cb, int[] fds);
}
