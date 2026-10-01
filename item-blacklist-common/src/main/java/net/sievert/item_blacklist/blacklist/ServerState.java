package net.sievert.item_blacklist.blacklist;

import net.sievert.item_blacklist.config.BlacklistConfig;
import net.sievert.item_blacklist.report.RemovalReport;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;

/**
 * Everything the mod keeps for one running server, created at its starting and dropped at its
 * stop, so nothing of one world reaches the next. Read by everything; written only by
 * Lifecycle and Blacklist, whose writes are plain field writes without a side effect.
 */
public final class ServerState {
    private final MinecraftServer server;
    private final BlacklistConfig config;
    private volatile BlacklistSnapshot snapshot;
    private final RemovalReport report = new RemovalReport();
    private final AtomicInteger reloads = new AtomicInteger();
    private volatile boolean startReloadRunning;
    private volatile boolean startReloadDone;
    private volatile boolean startReloadSucceeded;
    private final ConcurrentHashMap<Class<?>, Object> slots = new ConcurrentHashMap<>();

    ServerState(MinecraftServer server, BlacklistConfig config, BlacklistSnapshot first) {
        this.server = server;
        this.config = config;
        this.snapshot = first;
    }

    /** The server this state belongs to; states are keyed by its identity. */
    public MinecraftServer server() {
        return server;
    }

    /** The config as read for this server at its starting; immutable. */
    public BlacklistConfig config() {
        return config;
    }

    /** The current blacklist; a tag load replaces it whole. */
    public BlacklistSnapshot snapshot() {
        return snapshot;
    }

    /** The report, open for the whole life of this state and flushed after every reload. */
    public RemovalReport report() {
        return report;
    }

    /** Completed reloadResources calls the mod saw, the start reload included. */
    public int reloads() {
        return reloads.get();
    }

    /** Whether the forced reload of the server's start has reported back. */
    public boolean startReloadDone() {
        return startReloadDone;
    }

    /** Whether the forced reload of the server's start succeeded. */
    public boolean startReloadSucceeded() {
        return startReloadSucceeded;
    }

    /** Whether the caller runs on this server's thread: the tag hook's gate. */
    public boolean onServerThread() {
        return server.isSameThread();
    }

    /**
     * Per-server storage of a subsystem (brewing originals, a "warned once" flag), one value
     * per type, created on first use and dropped with the state. Safe from several threads,
     * since loot is parsed on workers; scenarios may use it with a type of their own.
     */
    public <T> T slot(Class<T> type, Supplier<T> create) {
        return type.cast(slots.computeIfAbsent(type, key -> create.get()));
    }

    /** Lifecycle and Blacklist only: the next snapshot. */
    void snapshot(BlacklistSnapshot next) {
        snapshot = next;
    }

    /** Lifecycle only: one more completed reload; the new count. */
    int reloadCompleted() {
        return reloads.incrementAndGet();
    }

    /** Lifecycle only: whether the forced start reload is under way. */
    boolean startReloadRunning() {
        return startReloadRunning;
    }

    /** Lifecycle only: the forced start reload begins. */
    void beginStartReload() {
        startReloadRunning = true;
    }

    /** Lifecycle only: the forced start reload reported back, with its outcome. */
    void finishStartReload(boolean succeeded) {
        startReloadSucceeded = succeeded;
        startReloadDone = true;
        startReloadRunning = false;
    }
}
