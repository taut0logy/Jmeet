package com.taut0logy.jmeet.meeting;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "meeting")
public class Meeting {

    @Id
    private String id;

    private String code;
    private String title;
    private String description;
    private String ownerId;

    @Enumerated(EnumType.STRING)
    private MeetingKind kind;

    @Enumerated(EnumType.STRING)
    private MeetingStatus status;

    private Instant startsAt;
    private Integer durationMin;
    private String timezone;
    private String rrule;
    private Instant seriesEndsAt;

    @Enumerated(EnumType.STRING)
    private MeetingAccess access;

    @Enumerated(EnumType.STRING)
    private WaitingRoomPolicy waitingRoom;

    private boolean allowGuests;
    private boolean muteOnEntry;
    private boolean cameraOffOnEntry;
    private Instant lockedAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected Meeting() {
    }

    public Meeting(String id, String code, String title, String ownerId, MeetingKind kind, Instant startsAt) {
        this.id = id;
        this.code = code;
        this.title = title;
        this.ownerId = ownerId;
        this.kind = kind;
        this.status = MeetingStatus.SCHEDULED;
        this.startsAt = startsAt;
        this.durationMin = 60;
        this.timezone = "UTC";
        this.access = MeetingAccess.LINK;
        this.waitingRoom = WaitingRoomPolicy.GUESTS_ONLY;
        this.allowGuests = true;
        this.muteOnEntry = false;
        this.cameraOffOnEntry = false;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public MeetingKind getKind() {
        return kind;
    }

    public MeetingStatus getStatus() {
        return status;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Integer getDurationMin() {
        return durationMin;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getRrule() {
        return rrule;
    }

    public Instant getSeriesEndsAt() {
        return seriesEndsAt;
    }

    public MeetingAccess getAccess() {
        return access;
    }

    public WaitingRoomPolicy getWaitingRoom() {
        return waitingRoom;
    }

    public boolean isAllowGuests() {
        return allowGuests;
    }

    public boolean isMuteOnEntry() {
        return muteOnEntry;
    }

    public boolean isCameraOffOnEntry() {
        return cameraOffOnEntry;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public boolean isRecurring() {
        return rrule != null;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public void setTitle(String title) {
        this.title = title;
        touch();
    }

    public void setDescription(String description) {
        this.description = description;
        touch();
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
        touch();
    }

    public void setDurationMin(Integer durationMin) {
        this.durationMin = durationMin;
        touch();
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
        touch();
    }

    public void setRrule(String rrule) {
        this.rrule = rrule;
        touch();
    }

    public void setSeriesEndsAt(Instant seriesEndsAt) {
        this.seriesEndsAt = seriesEndsAt;
        touch();
    }

    public void setAccess(MeetingAccess access) {
        this.access = access;
        touch();
    }

    public void setWaitingRoom(WaitingRoomPolicy waitingRoom) {
        this.waitingRoom = waitingRoom;
        touch();
    }

    public void setAllowGuests(boolean allowGuests) {
        this.allowGuests = allowGuests;
        touch();
    }

    public void setMuteOnEntry(boolean muteOnEntry) {
        this.muteOnEntry = muteOnEntry;
        touch();
    }

    public void setCameraOffOnEntry(boolean cameraOffOnEntry) {
        this.cameraOffOnEntry = cameraOffOnEntry;
        touch();
    }

    public void setStatus(MeetingStatus status) {
        this.status = status;
        touch();
    }

    public void lock() {
        this.lockedAt = Instant.now();
        touch();
    }

    public void unlock() {
        this.lockedAt = null;
        touch();
    }
}
