package git.artdeell.skymodloader;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.res.AssetManager;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;

import com.tgc.sky.ui.Utils;

import java.util.concurrent.LinkedBlockingQueue;

public class ImGUI implements SurfaceHolder.Callback{
    static ClipboardManager clipboard;
    static final ImGUIThread thread = new ImGUIThread();
    static {
        thread.setName("imgui dispatch thread");
        thread.start();
    }
    public static void setClipboardService(ClipboardManager service) {
        clipboard = service;
    }
    public static String getClipboard() {
        if(clipboard.hasPrimaryClip() && clipboard.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            CharSequence data = item.getText();
            if(data != null) return data.toString();
        }
        return "";
    }
    public static void setClipboard(String data) {
        clipboard.setPrimaryClip(ClipData.newPlainText("ImGui paste", data));
    }
    public static native void init(Surface surface, float fontSize, float scale, AssetManager smlAssets, boolean enableDroidSans);
    public static native void resurface(Surface surface);
    public static native void shutdown();
    public static native void submitPositionEvent(float x, float y);
    public static native void submitButtonEvent(int btn, boolean pressed);
    public static native void submitUnicodeEvent(char codepoint);
    public static native void submitKeyEvent(int key, boolean down);
    public static native boolean wantsKeyboard();
    public static native boolean wantsMouse();

    public void onKey(int keycode, boolean down) {
        submitKeyEvent(keycode, down);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
        thread.pushRunnable(()->{
            if(thread.hasInitialized) {
                resurface(surfaceHolder.getSurface());
            }else{
                thread.hasInitialized = true;
                init(surfaceHolder.getSurface(), Utils.sp2px(14), Utils.dp2px(1.2f), SMLApplication.smlRes.getAssets(), true);
            }
        });
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
        shutdown();
    }
    static class ImGUIThread extends Thread {
        LinkedBlockingQueue<Runnable> runnables = new LinkedBlockingQueue<>();
        volatile boolean hasInitialized = false;
        public void pushRunnable(Runnable runnable) {
            runnables.add(runnable);
        }
        @Override
        public void run() {
            try {
                while (true) {
                    runnables.take().run();
                }
            }catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
