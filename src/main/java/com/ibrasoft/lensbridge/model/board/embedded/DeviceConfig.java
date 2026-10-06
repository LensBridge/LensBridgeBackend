package com.ibrasoft.lensbridge.model.board.embedded;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.Location;
import jakarta.persistence.*;
import lombok.*;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "board_configs")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DeviceConfig {
    @Id
    private UUID id;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "device_id")
    private Device device;

    @Embedded
    private Location location;

    private boolean darkModeAfterIsha;
    private boolean enableScrollingMessage;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "board_config_messages",
        joinColumns = @JoinColumn(name = "device_id"))
    @Column(name = "message", length = MESSAGE_MAX)
    private List<@jakarta.validation.constraints.Size(max = MESSAGE_MAX,
            message = "Each scrolling message must be at most 4000 characters") String> scrollingMessages;

    /** Longest ticker message. Matches V4__widen_board_text_columns. */
    public static final int MESSAGE_MAX = 4000;

    /** Shortest and longest a slide may be pinned for. A 2s slide cannot be read; a 5m one is a hang. */
    public static final int MIN_SLIDE_SECONDS = 5;
    public static final int MAX_SLIDE_SECONDS = 120;

    /** Used whenever {@link #nextPrayerDurationSeconds} has no stored value. */
    public static final int DEFAULT_NEXT_PRAYER_DURATION_SECONDS = 12;

    /**
     * How long the agenda slide is pinned for, or null for "auto" — the board then grows the
     * duration with the number of events it is actually showing.
     *
     * The one genuinely nullable duration: an agenda with two events and one with a full week
     * do not deserve the same dwell time, and only the board knows which it is rendering.
     */
    @Column(name = "agenda_duration_seconds")
    private Integer agendaDurationSeconds;

    /**
     * How long the next-prayer countdown is pinned for. Never null to a caller — see the getter.
     *
     * The column is nullable because rows created before it existed have no value for it, and
     * backfilling them would store a duration nobody chose. The getter supplies the default
     * instead, so those rows read the same as a freshly created one.
     */
    @Column(name = "next_prayer_duration_seconds")
    private Integer nextPrayerDurationSeconds;

    /** Never returns null, so the payload always carries a concrete duration for this frame. */
    public int getNextPrayerDurationSeconds() {
        return nextPrayerDurationSeconds == null
                ? DEFAULT_NEXT_PRAYER_DURATION_SECONDS
                : nextPrayerDurationSeconds;
    }
}
