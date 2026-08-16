package com.ibrasoft.lensbridge.model.auth;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The role -> permission bundles. This is the whole authorization policy of the system,
 * in one file, on purpose: if you want to know what a role can do, you read this and
 * nothing else.
 * <p>
 * Bundles live here rather than in {@link Role}'s constructor because the JLS forbids an
 * enum constant's initializer from referencing another constant of the same enum, and the
 * bundles are defined by composition ({@code BOARD_ADMIN = BOARD_EDITOR ∪ …}).
 * <p>
 * There is deliberately no Spring {@code RoleHierarchy} bean. Inheritance is set union
 * performed here, once, at class-init time — a hierarchy string in security config could
 * silently disagree with these sets.
 */
final class RolePermissions {

    private static final Map<Role, Set<Permission>> BUNDLES = new EnumMap<>(Role.class);

    static {
        // ---------- Legacy media sharing ----------

        Set<Permission> user = EnumSet.of(
                Permission.MEDIA_UPLOAD_SELF);

        Set<Permission> admin = union(user, EnumSet.of(
                Permission.MEDIA_UPLOAD_MODERATE,
                Permission.MEDIA_UPLOAD_READ,
                Permission.MEDIA_EVENT_WRITE,
                Permission.AUDIT_READ));

        // ---------- MusallahBoard ----------

        // Read-only across the whole board surface. The role you hand someone so they can
        // tell you a board is offline without being able to touch it.
        Set<Permission> boardViewer = EnumSet.of(
                Permission.BOARD_CONTENT_READ,
                Permission.BOARD_CONFIG_READ,
                Permission.BOARD_DEVICE_READ,
                Permission.BOARD_TELEMETRY_SUBSCRIBE);

        // Can change anything that appears on a board, and nothing about the board itself.
        Set<Permission> boardEditor = union(boardViewer, EnumSet.of(
                Permission.BOARD_POSTER_WRITE,
                Permission.BOARD_EVENT_WRITE,
                Permission.BOARD_SOCIAL_WRITE,
                Permission.BOARD_WEEKLY_WRITE,
                Permission.BOARD_TICKER_WRITE,
                Permission.BOARD_REFRESH));

        // Owns the board product. Note the absence of everything under iam: — owning the
        // boards is not owning the org's accounts.
        Set<Permission> boardAdmin = union(boardEditor, EnumSet.of(
                Permission.BOARD_CONFIG_WRITE,
                Permission.BOARD_REFRESH,
                Permission.BOARD_COMMAND_BENIGN,
                Permission.BOARD_COMMAND_DISRUPTIVE,
                Permission.BOARD_DEVICE_ENROLL,
                Permission.BOARD_DEVICE_REVOKE,
                Permission.BOARD_COMMAND_INSPECT,
                Permission.AUDIT_READ));

        // ---------- Ticketing ----------

        // The door shift. Scan-only on purpose: a volunteer checking people in needs to
        // validate a QR and nothing else -- not the attendee roster, not the order book.
        Set<Permission> tcketScanner = EnumSet.of(
                Permission.TCKET_SCAN);

        // Runs an event end to end. Scans too, because whoever organises the event
        // inevitably ends up working the door.
        Set<Permission> tcketManager = union(tcketScanner, EnumSet.of(
                Permission.TCKET_MANAGE));

        // Owns the ticketing product. TCKET_ADMIN is separated from TCKET_MANAGE because it
        // covers settling payments by hand and deleting sold tickets -- money and
        // irreversibility, which is not the same trust as running an event.
        Set<Permission> tcketAdmin = union(tcketManager, EnumSet.of(
                Permission.TCKET_ADMIN));

        BUNDLES.put(Role.USER, user);
        BUNDLES.put(Role.ADMIN, admin);
        BUNDLES.put(Role.BOARD_VIEWER, boardViewer);
        BUNDLES.put(Role.BOARD_EDITOR, boardEditor);
        BUNDLES.put(Role.BOARD_ADMIN, boardAdmin);
        BUNDLES.put(Role.TCKET_SCANNER, tcketScanner);
        BUNDLES.put(Role.TCKET_MANAGER, tcketManager);
        BUNDLES.put(Role.TCKET_ADMIN, tcketAdmin);

        // ROOT is allOf rather than an explicit list so a newly added permission is held by
        // root on deploy. For a single-org deployment that is correct: the alternative locks
        // the owner out of every new feature until someone remembers to edit this file.
        // Revisit if Minbar ever becomes multi-tenant.
        BUNDLES.put(Role.ROOT, EnumSet.allOf(Permission.class));

        for (Role role : Role.values()) {
            if (!BUNDLES.containsKey(role)) {
                throw new IllegalStateException("Role " + role + " has no permission bundle");
            }
        }
    }

    private RolePermissions() {
    }

    static Set<Permission> of(Role role) {
        return Collections.unmodifiableSet(BUNDLES.get(role));
    }

    private static Set<Permission> union(Set<Permission> a, Set<Permission> b) {
        EnumSet<Permission> merged = EnumSet.copyOf(a);
        merged.addAll(b);
        return merged;
    }
}
