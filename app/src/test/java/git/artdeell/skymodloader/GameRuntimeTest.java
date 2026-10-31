package git.artdeell.skymodloader;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import git.artdeell.skymodloader.elfmod.ModManagerActivity;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = {26, 34, 36})
public class GameRuntimeTest {
    @Test
    public void repeatedBootstrapPreservesRegistration() throws Throwable {
        GameRuntime.Initialization state = new GameRuntime.Initialization();
        AtomicInteger registrations = new AtomicInteger();
        for (int i = 0; i < 10; i++) state.run(registrations::incrementAndGet);
        assertEquals(1, registrations.get());
        assertTrue(state.isReady());
    }

    @Test
    public void failedNativeInitializationCannotBeRetried() {
        GameRuntime.Initialization state = new GameRuntime.Initialization();
        IOException failure = new IOException("fixture");
        assertSame(failure, assertThrows(IOException.class, () -> state.run(() -> { throw failure; })));
        assertSame(failure, assertThrows(IOException.class, () -> state.run(() -> fail("retry"))));
        assertFalse(state.isReady());
    }

    @Test
    public void reentrantInitializationNeverRunsTheSecondInitializer() {
        GameRuntime.Initialization state = new GameRuntime.Initialization();
        assertThrows(IllegalStateException.class,
                () -> state.run(() -> state.run(() -> fail("reentered"))));
        assertFalse(state.isReady());
    }

    @Test
    public void concurrentRequestsWaitForTheSameInitialization() throws Exception {
        GameRuntime.Initialization state = new GameRuntime.Initialization();
        AtomicInteger registrations = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> {
                try {
                    state.run(() -> {
                        entered.countDown();
                        assertTrue(release.await(5, TimeUnit.SECONDS));
                        registrations.incrementAndGet();
                    });
                } catch (Throwable e) { throw new AssertionError(e); }
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var second = pool.submit(() -> {
                try { state.run(registrations::incrementAndGet); }
                catch (Throwable e) { throw new AssertionError(e); }
            });
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertEquals(1, registrations.get());
            assertTrue(state.isReady());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    public void staleBootstrapReturnsToManagerWithoutLoadingTheGame() {
        Intent stale = new Intent().putExtra("canvas.launch_session", "previous-process");
        var controller = Robolectric.buildActivity(MainActivity.class, stale).create(new Bundle());
        MainActivity activity = controller.get();
        Intent destination = shadowOf(activity).getNextStartedActivity();
        assertEquals(ModManagerActivity.class.getName(), destination.getComponent().getClassName());
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK, destination.getFlags());
        assertTrue(activity.isFinishing());
        assertFalse(GameRuntime.isReady());
        controller.destroy();
    }

    @Test
    public void playIntentBelongsToThisProcessOnly() {
        Application app = org.robolectric.RuntimeEnvironment.getApplication();
        assertTrue(GameRuntime.isCurrentLaunch(GameRuntime.launchIntent(app)));
        assertFalse(GameRuntime.isCurrentLaunch(new Intent()));
        assertFalse(GameRuntime.isCurrentLaunch(null));
        assertFalse(GameRuntime.isCurrentLaunch(new Intent().putExtra("canvas.launch_session", "old")));
    }
}
