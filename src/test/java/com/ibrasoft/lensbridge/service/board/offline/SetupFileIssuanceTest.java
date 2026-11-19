package com.ibrasoft.lensbridge.service.board.offline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import com.ibrasoft.lensbridge.repository.sql.SqliteDataJpaTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Setup-file issuance on a real database (SQLite, migrated like production) with real
 * commits: what a kit leaves stored when it fails, and what two downloads at once leave.
 * Not in a test transaction, so each issuance commits or rolls back on its own as in
 * production; the rows are deleted afterwards.
 */
@SqliteDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SetupFileIssuanceTest {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00.250Z");
    private static final long OLD_SEQUENCE = 1_700_000_000_000L;
    private static final String BACKEND = "https://api.example.org";

    @Autowired
    private DeviceRepository repository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final ContentSigningService signer = new ContentSigningService(
            Base64.getEncoder().encodeToString(ContentSigningServiceTest.VECTOR_SEED), "");
    private final byte[] contentKey = Base64.getDecoder().decode(signer.publicKeys().get(0).getPublicKey());
    private final List<UUID> created = new ArrayList<>();

    @AfterEach
    void deleteDevices() {
        repository.deleteAllById(created);
    }

    private Device board(String name, byte oldSecret) {
        byte[] secret = new byte[32];
        Arrays.fill(secret, oldSecret);
        Device device = repository.save(Device.builder().displayName(name).audience(Audience.BOTH)
                .provisionSequence(OLD_SEQUENCE).claimSecret(secret).build());
        created.add(device.getId());
        return device;
    }

    /** The setup service as wired in production, whose @Transactional the template stands in for. */
    private SetupFileService setupFiles(Clock clock) {
        return new SetupFileService(repository, objectMapper, signer, BACKEND, clock, new SecureRandom());
    }

    private OfflineBundle issue(SetupFileService service, UUID deviceId) {
        return new TransactionTemplate(transactionManager).execute(s -> service.build(deviceId, true));
    }

    private int advance(UUID id, long now) {
        Integer rows = new TransactionTemplate(transactionManager).execute(s -> repository.advanceProvisionSequence(id, now));
        return rows == null ? -1 : rows;
    }

    private UsbKitService kit(OfflineBundleService content) {
        ReleaseChannelCache releases = mock(ReleaseChannelCache.class);
        when(releases.latest(any())).thenReturn(new ReleaseChannelCache.Lookup(null, "offline"));
        return new UsbKitService(content, setupFiles(Clock.fixed(NOW, ZoneOffset.UTC)), releases, signer,
                transactionManager);
    }

    private static OfflineBundle pkg(String marker) {
        return new OfflineBundle(marker, List.of(new OfflineBundle.Entry("mbu.json", marker.getBytes(), false)));
    }

    private void assertUnchanged(Device before) {
        Device now = repository.findById(before.getId()).orElseThrow();
        assertThat(now.getProvisionSequence()).as(before.getDisplayName() + " sequence").isEqualTo(OLD_SEQUENCE);
        assertThat(now.getClaimSecret()).as(before.getDisplayName() + " secret").isEqualTo(before.getClaimSecret());
    }

    private record Issued(long sequence, String claimSecret) {}

    private Issued read(OfflineBundle file) throws Exception {
        return readBytes(AgentPackageRules.bytesOf(file));
    }

    private Issued readBytes(byte[] file) throws Exception {
        AgentPackageRules.Verified pkg = AgentPackageRules.verify(file, contentKey);
        var json = new ObjectMapper().readTree(pkg.entries().get(SetupFileService.PROVISION_ENTRY));
        return new Issued(json.get("sequence").asLong(), json.get("claimSecret").asText());
    }

    private static String encoded(byte[] secret) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    @Test
    void aKitWhoseLastBoardsContentFailsChangesNoBoard() {
        List<Device> boards = List.of(board("A", (byte) 1), board("B", (byte) 2), board("C", (byte) 3));
        OfflineBundleService content = mock(OfflineBundleService.class);
        when(content.build(any(), anyInt())).thenReturn(pkg("c"));
        when(content.build(eq(boards.get(2).getId()), anyInt())).thenThrow(
                new ApiResponseException(HttpStatus.BAD_GATEWAY, ErrorResponse.of("poster")));

        assertThatThrownBy(() -> kit(content).build(boards, "kit.zip", 14, true, true))
                .isInstanceOf(ApiResponseException.class);

        boards.forEach(this::assertUnchanged);
    }

    /** The last board's setup file fails after the others were issued: all of them roll back. */
    @Test
    void aKitWhoseLastSetupFileFailsRollsBackEveryBoard() {
        List<Device> boards = new ArrayList<>(List.of(board("A", (byte) 1), board("B", (byte) 2), board("C", (byte) 3)));
        // Setup files are issued in id order; revoke the last one there (after its content was built).
        Device last = boards.stream().max(Comparator.comparing(Device::getId)).orElseThrow();
        OfflineBundleService content = mock(OfflineBundleService.class);
        when(content.build(any(), anyInt())).thenAnswer(inv -> {
            if (inv.getArgument(0).equals(last.getId())) {
                Device d = repository.findById(last.getId()).orElseThrow();
                d.setRevokedAt(NOW);
                repository.save(d);
            }
            return pkg("c");
        });

        assertThatThrownBy(() -> kit(content).build(boards, "kit.zip", 14, true, true))
                .isInstanceOfSatisfying(ApiResponseException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        boards.forEach(this::assertUnchanged);
    }

    @Test
    void aCompleteKitIssuesEveryBoardANewSequenceAndSecret() throws Exception {
        List<Device> boards = List.of(board("A", (byte) 1), board("B", (byte) 2));
        OfflineBundleService content = mock(OfflineBundleService.class);
        when(content.build(any(), anyInt())).thenReturn(pkg("c"));

        try (UsbKitService.Kit kit = kit(content).build(boards, "kit.zip", 14, true, true)) {
            var zip = AgentPackageRules.unzip(java.nio.file.Files.readAllBytes(kit.file()));
            for (Device before : boards) {
                Device now = repository.findById(before.getId()).orElseThrow();
                byte[] setup = zip.get(UsbKitService.FOLDER + "setup-" + before.getId().toString().substring(0, 8) + ".mbu");
                Issued issued = readBytes(setup);
                assertThat(now.getProvisionSequence()).isEqualTo(issued.sequence()).isEqualTo(NOW.toEpochMilli());
                assertThat(encoded(now.getClaimSecret())).isEqualTo(issued.claimSecret());
            }
        }
    }

    /**
     * Downloads racing for one board (a standalone file and a kit, say): each gets its own,
     * strictly increasing sequence, and the stored secret is the one in the newest file. The
     * clock is frozen, so every file is in the same millisecond and only the row lock keeps
     * them apart.
     */
    @Test
    void concurrentIssuancesForOneBoardAreSerialized() throws Exception {
        Device board = board("North", (byte) 9);
        SetupFileService service = setupFiles(Clock.fixed(NOW, ZoneOffset.UTC));
        int threads = 4;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Issued>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return read(issue(service, board.getId()));
                }));
            }
            start.countDown();
            List<Issued> issued = new ArrayList<>();
            for (Future<Issued> f : futures) issued.add(f.get(30, TimeUnit.SECONDS));

            List<Long> sequences = issued.stream().map(Issued::sequence).sorted().toList();
            assertThat(sequences).doesNotHaveDuplicates()
                    .containsExactly(NOW.toEpochMilli(), NOW.toEpochMilli() + 1, NOW.toEpochMilli() + 2,
                            NOW.toEpochMilli() + 3);
            Issued newest = issued.stream().max(Comparator.comparingLong(Issued::sequence)).orElseThrow();
            Device stored = repository.findById(board.getId()).orElseThrow();
            assertThat(stored.getProvisionSequence()).isEqualTo(newest.sequence());
            assertThat(encoded(stored.getClaimSecret())).isEqualTo(newest.claimSecret());
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * One download holds the row (issued, not yet committed) while another starts: the second
     * waits for the first to commit and counts from it, rather than reading the old sequence.
     */
    @Test
    void anIssuanceStartedWhileAnotherIsUncommittedCountsFromIt() throws Exception {
        Device board = board("North", (byte) 9);
        SetupFileService service = setupFiles(Clock.fixed(NOW, ZoneOffset.UTC));
        CountDownLatch firstIssued = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Issued> first = pool.submit(() -> read(new TransactionTemplate(transactionManager).<OfflineBundle>execute(s -> {
                OfflineBundle file = service.build(board.getId(), true);
                firstIssued.countDown();
                try {
                    Thread.sleep(500); // still uncommitted while the second one starts
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return file;
            })));
            assertThat(firstIssued.await(10, TimeUnit.SECONDS)).isTrue();
            Issued second = read(issue(service, board.getId()));

            assertThat(second.sequence()).isEqualTo(first.get(10, TimeUnit.SECONDS).sequence() + 1);
            Device stored = repository.findById(board.getId()).orElseThrow();
            assertThat(stored.getProvisionSequence()).isEqualTo(second.sequence());
            assertThat(encoded(stored.getClaimSecret())).isEqualTo(second.claimSecret());
        } finally {
            pool.shutdownNow();
        }
    }

    /** The SQL of the sequence: now when greater, else one more; a revoked or unknown board is left alone. */
    @Test
    void theSequenceUpdateCountsOnFromTheLastOrTheClock() {
        Device fresh = repository.save(Device.builder().displayName("Fresh").audience(Audience.BOTH).build());
        created.add(fresh.getId());
        Device revoked = board("Gone", (byte) 1);
        revoked.setRevokedAt(NOW);
        repository.save(revoked);

        assertThat(advance(fresh.getId(), 5000)).isEqualTo(1);
        assertThat(repository.findById(fresh.getId()).orElseThrow().getProvisionSequence()).isEqualTo(5000);
        advance(fresh.getId(), 5000);
        assertThat(repository.findById(fresh.getId()).orElseThrow().getProvisionSequence()).isEqualTo(5001);
        advance(fresh.getId(), 10); // clock went back
        assertThat(repository.findById(fresh.getId()).orElseThrow().getProvisionSequence()).isEqualTo(5002);
        advance(fresh.getId(), 9000);
        assertThat(repository.findById(fresh.getId()).orElseThrow().getProvisionSequence()).isEqualTo(9000);

        assertThat(advance(revoked.getId(), 9000)).isZero();
        assertThat(repository.findById(revoked.getId()).orElseThrow().getProvisionSequence()).isEqualTo(OLD_SEQUENCE);
        assertThat(advance(UUID.randomUUID(), 9000)).isZero();
    }
}
