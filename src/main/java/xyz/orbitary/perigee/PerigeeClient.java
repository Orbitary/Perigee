/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee;

import org.jetbrains.annotations.Nullable;
import xyz.orbitary.perigee.activity.Activity;
import xyz.orbitary.perigee.event.Disconnected;
import xyz.orbitary.perigee.event.Join;
import xyz.orbitary.perigee.event.JoinRequest;
import xyz.orbitary.perigee.event.PerigeeEvent;
import xyz.orbitary.perigee.event.Ready;
import xyz.orbitary.perigee.event.Spectate;
import xyz.orbitary.perigee.event.Subscription;
import xyz.orbitary.perigee.internal.Connection;
import xyz.orbitary.perigee.internal.PresenceScheduler;
import xyz.orbitary.perigee.protocol.ActivityPayload;
import xyz.orbitary.perigee.protocol.Requests;
import xyz.orbitary.perigee.protocol.RpcMessage;
import xyz.orbitary.perigee.transport.EndpointResolver;
import xyz.orbitary.perigee.transport.Transport;
import xyz.orbitary.perigee.transport.TransportConnector;
import xyz.orbitary.perigee.user.DiscordUser;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;


/**
 * Discord Rich Presence.
 */
public final class PerigeeClient implements AutoCloseable {
    @FunctionalInterface
    interface TransportOpener {
        Transport open() throws IOException;

    }

    private static final List<String> EVENTS = List.of(Requests.EVENT_JOIN, Requests.EVENT_SPECTATE, Requests.EVENT_JOIN_REQUEST);

    private record Handler<E extends PerigeeEvent>(
      Class<E> type,
      Consumer<? super E> consumer
    ) {
        void accept(PerigeeEvent event) {
            if (type.isInstance(event)) consumer.accept(type.cast(event));
        }

    }

    private final String applicationId;
    private final boolean autoReconnect;
    private final Duration minDelay;
    private final Duration maxDelay;
    private final TransportOpener opener;
    private final Connection.Timeouts timeouts;
    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1, task -> Thread.ofPlatform().daemon().name("perigee-scheduler").unstarted(task));
    private final Executor events;
    private final @Nullable ExecutorService ownedEvents;
    private final PresenceScheduler presence = new PresenceScheduler(scheduler, this::sendActivity);
    private final List<Handler<?>> handlers = new CopyOnWriteArrayList<>();
    private final CompletableFuture<DiscordUser> connectFuture = new CompletableFuture<>();

    private final Object lock = new Object();
    private @Nullable Connection connection;
    private ConnectionState state = ConnectionState.IDLE;
    private @Nullable Ready currentReady;
    private boolean everReady;
    private int attempts;
    private boolean outageLogged;

    private final Connection.Listener listener = new Connection.Listener() {
        @Override
        public void onReady(DiscordUser user) {
            PerigeeClient.this.onReady(user);
        }

        @Override
        public void onEvent(RpcMessage message) {
            PerigeeClient.this.onEvent(message);
        }

        @Override
        public void onClosed(@Nullable Throwable cause) {
            onDropped(cause);
        }
    };

    private PerigeeClient(Builder builder) {
        this.applicationId = builder.applicationId;
        this.autoReconnect = builder.autoReconnect;
        this.minDelay = builder.minDelay;
        this.maxDelay = builder.maxDelay;
        this.opener = builder.opener();
        this.timeouts = builder.timeouts;
        if (builder.eventExecutor != null) {
            this.events = builder.eventExecutor;
            this.ownedEvents = null;
        } else {
            this.ownedEvents = Executors.newSingleThreadExecutor(task -> Thread.ofPlatform().daemon().name("perigee-events").unstarted(task));
            this.events = ownedEvents;
        }
    }

    public static Builder builder(String applicationId) {
        return new Builder(applicationId);
    }

    public static Builder builder(long applicationId) {
        return new Builder(Long.toString(applicationId));
    }

    /**
     * Starts connecting. The future completes with the Discord user once the handshake finishes.
     */
    public CompletableFuture<DiscordUser> connect() {
        synchronized (lock) {
            if (state == ConnectionState.CLOSED) return CompletableFuture.failedFuture(new PerigeeException("Client is closed"));
            if (state == ConnectionState.IDLE) {
                state = ConnectionState.CONNECTING;
                scheduler.execute(this::attempt);
            }
        }
        return connectFuture;
    }

    /**
     * Like {@link #connect()}, but the returned future fails with a {@link PerigeeTimeoutException} if Discord is not ready in time.
     * The client keeps trying in the background; call {@link #close()} to stop.
     */
    public CompletableFuture<DiscordUser> connect(Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        CompletableFuture<DiscordUser> source = connect();
        if (source.isDone()) return source;
        CompletableFuture<DiscordUser> result = new CompletableFuture<>();
        ScheduledFuture<?> expiry;
        try {
            expiry = scheduler.schedule(() -> result.completeExceptionally(new PerigeeTimeoutException("Discord was not ready within " + timeout)), timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            return source;
        }
        source.whenComplete((user, error) -> {
            expiry.cancel(false);
            if (error == null) {
                result.complete(user);
            } else {
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    /**
     * Sets the presence. Updates are rate limited and coalesced: only the newest is sent, and every future for a coalesced update completes with it.
     * An activity equal to the one last sent on this connection is not sent again, but its future still completes.
     */
    public CompletableFuture<Void> setActivity(Activity activity) {
        warnIfGatewayOnly(activity);
        return presence.submit(activity);
    }

    public CompletableFuture<Void> setActivity(Consumer<Activity.Builder> configure) {
        return setActivity(Activity.of(configure));
    }

    /**
     * Changes the last requested activity, or an empty one if there is none, as one atomic step.
     * The function runs while holding an internal lock, so keep it quick and do not call back into the client.
     */
    public CompletableFuture<Void> updateActivity(UnaryOperator<Activity.Builder> update) {
        return presence.update(current -> {
            Activity next = update.apply(current == null ? Activity.builder() : current.toBuilder()).build();
            warnIfGatewayOnly(next);
            return next;
        });
    }

    public CompletableFuture<Void> clearActivity() {
        return presence.submit(null);
    }

    public CompletableFuture<Void> acceptJoinRequest(String userId) {
        return command(Requests.ACCEPT_JOIN, Map.of("user_id", userId));
    }

    public CompletableFuture<Void> rejectJoinRequest(String userId) {
        return command(Requests.REJECT_JOIN, Map.of("user_id", userId));
    }

    /**
     * Registers a handler. All handlers run one after another on the {@code perigee-events} thread, or on the executor given to
     * {@link Builder#eventExecutor(Executor)}, so a slow handler delays the others.
     */
    public <E extends PerigeeEvent> Subscription on(Class<E> type, Consumer<? super E> consumer) {
        return on(type, consumer, false);
    }

    public <E extends PerigeeEvent> Subscription on(Class<E> type, Consumer<? super E> consumer, boolean replayLast) {
        Handler<E> handler = new Handler<>(type, consumer);
        Ready replay;
        synchronized (lock) {
            handlers.add(handler);
            replay = replayLast ? currentReady : null;
        }
        if (replay != null) dispatch(List.of(handler), replay);
        return () -> handlers.remove(handler);
    }

    public boolean isConnected() {
        return isReady();
    }

    public boolean isReady() {
        return state() == ConnectionState.READY;
    }

    public ConnectionState state() {
        synchronized (lock) {
            return state;
        }
    }

    /**
     * Disconnects and releases all threads. Blocks briefly while Discord acknowledges the close.
     */
    @Override
    public void close() {
        Connection current;
        synchronized (lock) {
            if (state == ConnectionState.CLOSED) return;
            state = ConnectionState.CLOSED;
            currentReady = null;
            current = connection;
            connection = null;
        }
        presence.close();
        connectFuture.completeExceptionally(new PerigeeException("Client is closed"));
        if (current != null) current.close();
        scheduler.shutdown();
        if (ownedEvents != null) ownedEvents.shutdown();
    }

    private void attempt() {
        Connection created = null;
        try {
            Transport transport = opener.open();
            created = new Connection(transport, listener, scheduler, timeouts);
            synchronized (lock) {
                if (state == ConnectionState.CLOSED) {
                    created.close();
                    return;
                }
                state = ConnectionState.CONNECTING;
                connection = created;
            }
            created.start(applicationId);
        } catch (IOException | RuntimeException e) {
            if (created != null) created.close();
            onDropped(e);
        }
    }

    private void onReady(DiscordUser user) {
        Connection current;
        Ready readyEvent = new Ready(user);
        List<Handler<?>> snapshot;
        synchronized (lock) {
            if (state != ConnectionState.CLOSED) {
                state = ConnectionState.READY;
                currentReady = readyEvent;
            }
            everReady = true;
            attempts = 0;
            outageLogged = false;
            current = connection;
            snapshot = new ArrayList<>(handlers);
        }
        presence.setOnline(true);
        if (current != null) {
            for (String event : EVENTS) {
                current.request(Requests.SUBSCRIBE, Map.of(), event).whenComplete((message, error) -> {
                    if (error != null) Logger.warn("Could not subscribe to " + event, error);
                });
            }
        }
        connectFuture.complete(user);
        dispatch(snapshot, readyEvent);
    }

    private void onEvent(RpcMessage message) {
        Map<String, Object> data = message.data();
        String event = message.evt() == null ? "" : message.evt();
        switch (event) {
            case Requests.EVENT_JOIN -> fire(new Join(String.valueOf(data.get("secret"))));
            case Requests.EVENT_SPECTATE -> fire(new Spectate(String.valueOf(data.get("secret"))));
            case Requests.EVENT_JOIN_REQUEST -> fire(new JoinRequest(RpcMessage.user(RpcMessage.map(data, "user"))));
            default -> Logger.debug("Ignoring event {0}", event);
        }
    }

    private void onDropped(@Nullable Throwable cause) {
        boolean wasReady;
        boolean stop;
        boolean fatal;
        boolean retry;
        boolean logOutage = false;
        long delay = 0;
        synchronized (lock) {
            connection = null;
            currentReady = null;
            wasReady = state == ConnectionState.READY;
            stop = state == ConnectionState.CLOSED;
            fatal = !everReady && cause instanceof RpcException rpc && rpc.isFatal();
            retry = !stop && !fatal && autoReconnect;
            if (retry) {
                state = ConnectionState.BACKOFF;
                int attempt = attempts++;
                delay = cause instanceof RpcException rpc && rpc.isRateLimited() ? maxDelay.toMillis() : backoff(attempt);
                logOutage = !outageLogged;
                outageLogged = true;
            }
        }
        presence.setOnline(false);
        if (stop) return;
        if (wasReady) fire(new Disconnected(cause, retry));
        if (fatal && cause != null) Logger.warn("Discord rejected the connection and Perigee will not retry: {0}", String.valueOf(cause.getMessage()));
        if (logOutage) {
            if (wasReady) {
                Logger.warn("Lost the connection to Discord ({0}); retrying in the background", cause == null ? "closed" : String.valueOf(cause.getMessage()));
            } else {
                Logger.debug("Discord is not reachable; retrying in the background");
            }
        }
        if (retry) {
            scheduler.schedule(this::attempt, delay, TimeUnit.MILLISECONDS);
            return;
        }
        connectFuture.completeExceptionally(fatal ? cause : new PerigeeException("Unable to reach Discord", cause));
        close();
    }

    private long backoff(int attempt) {
        long millis = minDelay.toMillis() << Math.min(attempt, 16);
        return Math.min(millis, maxDelay.toMillis());
    }

    private CompletableFuture<Void> sendActivity(@Nullable Activity activity) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("pid", ProcessHandle.current().pid());
        if (activity != null) args.put("activity", ActivityPayload.toMap(activity));
        return command(Requests.SET_ACTIVITY, args);
    }

    private CompletableFuture<Void> command(String cmd, Map<String, Object> args) {
        Connection current;
        synchronized (lock) {
            current = state == ConnectionState.READY ? connection : null;
        }
        if (current == null) return CompletableFuture.failedFuture(new PerigeeException("Not connected to Discord"));
        return current.request(cmd, args).thenAccept(message -> {});
    }

    private void warnIfGatewayOnly(Activity activity) {
        if (!activity.type().rpcSupported()) {
            Logger.warn("Activity type {0} is documented as gateway-only; Discord may reject it over IPC", activity.type());
        }
    }

    private void fire(PerigeeEvent event) {
        dispatch(new ArrayList<>(handlers), event);
    }

    private void dispatch(List<Handler<?>> targets, PerigeeEvent event) {
        try {
            events.execute(() -> {
                for (Handler<?> handler : targets) {
                    try {
                        handler.accept(event);
                    } catch (RuntimeException e) {
                        Logger.warn("Event handler failed for " + event, e);
                    }
                }
            });
        } catch (RejectedExecutionException ignored) {}
    }

    public static final class Builder {
        private final String applicationId;
        private boolean autoReconnect = true;
        private Duration minDelay = Duration.ofSeconds(1);
        private Duration maxDelay = Duration.ofSeconds(30);
        private final List<String> searchDirectories = new ArrayList<>();
        private int preferredIndex;
        private @Nullable Executor eventExecutor;
        private @Nullable TransportOpener opener;
        private Connection.Timeouts timeouts = Connection.Timeouts.DEFAULT;

        private Builder(String applicationId) {
            if (applicationId.isEmpty() || !applicationId.chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("applicationId must be the numeric Discord application ID");
            }
            this.applicationId = applicationId;
        }

        public Builder autoReconnect(boolean autoReconnect) {
            this.autoReconnect = autoReconnect;
            return this;
        }

        public Builder reconnectDelay(Duration min, Duration max) {
            if (min.isNegative() || min.isZero() || max.compareTo(min) < 0) throw new IllegalArgumentException("require 0 < min <= max");
            this.minDelay = min;
            this.maxDelay = max;
            return this;
        }

        public Builder searchDirectory(String directory) {
            this.searchDirectories.add(directory);
            return this;
        }

        public Builder preferredIndex(int preferredIndex) {
            if (preferredIndex < 0 || preferredIndex > EndpointResolver.MAX_INDEX) throw new IllegalArgumentException("preferredIndex must be between 0 and " + EndpointResolver.MAX_INDEX);
            this.preferredIndex = preferredIndex;
            return this;
        }

        public Builder eventExecutor(Executor eventExecutor) {
            this.eventExecutor = eventExecutor;
            return this;
        }

        Builder transport(TransportOpener opener) {
            this.opener = opener;
            return this;
        }

        Builder timeouts(Connection.Timeouts timeouts) {
            this.timeouts = timeouts;
            return this;
        }

        public PerigeeClient build() {
            return new PerigeeClient(this);
        }

        private TransportOpener opener() {
            if (opener != null) return opener;
            boolean windows = EndpointResolver.isWindows();
            Map<String, String> env = System.getenv();
            List<String> directories = List.copyOf(searchDirectories);
            int preferred = preferredIndex;
            return () -> TransportConnector.connect(EndpointResolver.resolve(windows, env, directories, preferred), windows);
        }

    }

}
