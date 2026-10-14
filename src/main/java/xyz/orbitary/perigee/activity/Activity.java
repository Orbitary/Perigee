/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.orbitary.perigee.activity;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;


/**
 * An immutable Rich Presence payload. Unset fields are omitted from what Discord receives.
 */
public record Activity(
  @Nullable String name,
  ActivityType type,
  @Nullable String details,
  @Nullable String detailsUrl,
  @Nullable String state,
  @Nullable String stateUrl,
  @Nullable StatusDisplayType statusDisplayType,
  @Nullable String url,
  @Nullable Timestamps timestamps,
  @Nullable Party party,
  Assets assets,
  Secrets secrets,
  List<Button> buttons,
  boolean instance,
  Set<ActivityFlag> flags
) {
    public static final int MAX_BUTTONS = 2;

    public Activity {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(assets, "assets");
        Objects.requireNonNull(secrets, "secrets");
        Objects.requireNonNull(buttons, "buttons");
        Objects.requireNonNull(flags, "flags");
        if (type == ActivityType.CUSTOM) throw new IllegalArgumentException("CUSTOM activities are not supported over IPC");
        name = Validation.text("name", name);
        details = Validation.text("details", details);
        state = Validation.text("state", state);
        detailsUrl = Validation.url("details url", detailsUrl);
        stateUrl = Validation.url("state url", stateUrl);
        url = Validation.url("url", url);
        if (url != null && type != ActivityType.STREAMING) throw new IllegalArgumentException("url is only valid for STREAMING activities");
        if (buttons.size() > MAX_BUTTONS) throw new IllegalArgumentException("at most " + MAX_BUTTONS + " buttons are allowed");
        buttons = List.copyOf(buttons);
        flags = flags.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(flags));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Activity of(Consumer<? super Builder> configure) {
        Builder builder = new Builder();
        configure.accept(builder);
        return builder.build();
    }

    public static final class Builder {
        private @Nullable String name;
        private ActivityType type = ActivityType.PLAYING;
        private @Nullable String details;
        private @Nullable String detailsUrl;
        private @Nullable String state;
        private @Nullable String stateUrl;
        private @Nullable StatusDisplayType statusDisplayType;
        private @Nullable String url;
        private @Nullable Timestamps timestamps;
        private @Nullable Party party;
        private Assets assets = Assets.EMPTY;
        private Secrets secrets = Secrets.EMPTY;
        private final List<Button> buttons = new ArrayList<>();
        private boolean instance;
        private final Set<ActivityFlag> flags = EnumSet.noneOf(ActivityFlag.class);

        private Builder() {}

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(ActivityType type) {
            this.type = type;
            return this;
        }

        public Builder details(String details) {
            this.details = details;
            return this;
        }

        public Builder detailsUrl(String detailsUrl) {
            this.detailsUrl = detailsUrl;
            return this;
        }

        public Builder state(String state) {
            this.state = state;
            return this;
        }

        public Builder stateUrl(String stateUrl) {
            this.stateUrl = stateUrl;
            return this;
        }

        public Builder statusDisplay(StatusDisplayType statusDisplayType) {
            this.statusDisplayType = statusDisplayType;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder timestamps(Timestamps timestamps) {
            this.timestamps = timestamps;
            return this;
        }

        public Builder startedAt(Instant start) {
            return timestamps(Timestamps.since(start));
        }

        public Builder endsAt(Instant end) {
            return timestamps(Timestamps.until(end));
        }

        public Builder party(Party party) {
            this.party = party;
            return this;
        }

        public Builder party(String id, int size, int max) {
            return party(Party.of(id, size, max));
        }

        public Builder assets(Assets assets) {
            this.assets = assets;
            return this;
        }

        public Builder largeImage(String image, @Nullable String text) {
            this.assets = assets.withLarge(image, text);
            return this;
        }

        public Builder largeImage(String image) {
            return largeImage(image, null);
        }

        public Builder largeUrl(String url) {
            this.assets = assets.withLargeUrl(url);
            return this;
        }

        public Builder smallImage(String image, @Nullable String text) {
            this.assets = assets.withSmall(image, text);
            return this;
        }

        public Builder smallImage(String image) {
            return smallImage(image, null);
        }

        public Builder smallUrl(String url) {
            this.assets = assets.withSmallUrl(url);
            return this;
        }

        public Builder inviteCoverImage(String image) {
            this.assets = assets.withInviteCoverImage(image);
            return this;
        }

        public Builder secrets(Secrets secrets) {
            this.secrets = secrets;
            return this;
        }

        public Builder joinSecret(String secret) {
            this.secrets = secrets.withJoin(secret);
            return this;
        }

        public Builder spectateSecret(String secret) {
            this.secrets = secrets.withSpectate(secret);
            return this;
        }

        public Builder matchSecret(String secret) {
            this.secrets = secrets.withMatch(secret);
            return this;
        }

        public Builder button(Button button) {
            if (buttons.size() >= MAX_BUTTONS) throw new IllegalArgumentException("at most " + MAX_BUTTONS + " buttons are allowed");
            this.buttons.add(Objects.requireNonNull(button, "button"));
            return this;
        }

        public Builder button(String label, String url) {
            return button(new Button(label, url));
        }

        public Builder instance(boolean instance) {
            this.instance = instance;
            return this;
        }

        public Builder flag(ActivityFlag... flags) {
            Collections.addAll(this.flags, flags);
            return this;
        }


        public Activity build() {
            return new Activity(name, type, details, detailsUrl, state, stateUrl, statusDisplayType, url, timestamps, party, assets, secrets, buttons, instance, flags);
        }

    }

}
