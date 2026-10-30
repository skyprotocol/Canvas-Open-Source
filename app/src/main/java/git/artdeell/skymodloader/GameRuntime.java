package git.artdeell.skymodloader;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import java.util.UUID;

import git.artdeell.skymodloader.elfmod.ModManagerActivity;

/** Process lifetime state; never restore this from an Activity's saved state. */
public final class GameRuntime {
    private static final String LAUNCH_SESSION = "canvas.launch_session";
    private static final String SESSION = UUID.randomUUID().toString();
    private static final Initialization INITIALIZATION = new Initialization();

    private GameRuntime() {}

    public static Intent launchIntent(Context context) {
        return new Intent(context, MainActivity.class).putExtra(LAUNCH_SESSION, SESSION);
    }

    public static boolean isCurrentLaunch(Intent intent) {
        return intent != null && SESSION.equals(intent.getStringExtra(LAUNCH_SESSION));
    }

    public static void initialize(Initializer initializer) throws Throwable {
        INITIALIZATION.run(initializer);
    }

    public static boolean isReady() {
        return INITIALIZATION.isReady() && SMLApplication.skyRes != null;
    }

    public static void returnToManager(Activity activity) {
        activity.startActivity(new Intent(activity, ModManagerActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity.finish();
    }

    @FunctionalInterface
    public interface Initializer {
        void run() throws Throwable;
    }

    static final class Initialization {
        private enum State { NEW, INITIALIZING, READY, FAILED }
        private State state = State.NEW;
        private Throwable failure;

        synchronized void run(Initializer initializer) throws Throwable {
            if (state == State.READY) return;
            if (state == State.FAILED) throw failure;
            if (state == State.INITIALIZING) {
                throw new IllegalStateException("Reentrant game initialization");
            }
            state = State.INITIALIZING;
            try {
                initializer.run();
                state = State.READY;
            } catch (Throwable error) {
                // Native loads and mod hooks cannot be rolled back for a retry.
                failure = error;
                state = State.FAILED;
                throw error;
            }
        }

        synchronized boolean isReady() {
            return state == State.READY;
        }
    }
}
