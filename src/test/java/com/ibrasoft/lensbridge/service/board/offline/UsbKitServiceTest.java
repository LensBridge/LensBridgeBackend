package com.ibrasoft.lensbridge.service.board.offline;

import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsbKitServiceTest {

    private static final Device A = device("aaaaaaaa-0000-4000-8000-000000000001", "North");
    private static final Device B = device("bbbbbbbb-0000-4000-8000-000000000002", "South");

    private final OfflineBundleService content = mock(OfflineBundleService.class);
    private final SetupFileService setup = mock(SetupFileService.class);
    private final ReleaseChannelCache releases = mock(ReleaseChannelCache.class);
    private final UsbKitService service = new UsbKitService(content, setup, releases,
            new ContentSigningService(Base64.getEncoder().encodeToString(ContentSigningServiceTest.VECTOR_SEED), ""));

    private static Device device(String id, String name) {
        return Device.builder().id(UUID.fromString(id)).displayName(name).audience(Audience.BOTH).build();
    }

    private static OfflineBundle pkg(String marker) {
        return new OfflineBundle(marker, List.of(new OfflineBundle.Entry("mbu.json",
                marker.getBytes(StandardCharsets.UTF_8), false)));
    }

    private void releasesAvailable() {
        when(releases.latest(ReleaseChannelCache.Kind.APP)).thenReturn(new ReleaseChannelCache.Lookup(
                new ReleaseChannelCache.ReleasePackage(ReleaseChannelCache.Kind.APP, "2.1.0",
                        "musallahboard-app-2.1.0.mbu", new byte[]{1}), null));
        when(releases.latest(ReleaseChannelCache.Kind.AGENT)).thenReturn(new ReleaseChannelCache.Lookup(
                new ReleaseChannelCache.ReleasePackage(ReleaseChannelCache.Kind.AGENT, "0.5.0",
                        "musallahboard-agent-0.5.0-arm64.mbu", new byte[]{2}), null));
    }

    private Map<String, byte[]> entries(UsbKitService.Kit kit) throws Exception {
        try (kit) {
            return AgentPackageRules.unzip(Files.readAllBytes(kit.file()));
        }
    }

    @Test
    void aKitIsOneMusallahBoardFolderWithEveryBoardsFilesAndTheLatestSoftware() throws Exception {
        when(content.build(any(), anyInt())).thenAnswer(inv -> pkg("content " + inv.getArgument(0)));
        when(setup.build(any(), anyBoolean())).thenAnswer(inv -> pkg("setup " + inv.getArgument(0)));
        releasesAvailable();

        UsbKitService.Kit kit = service.build(List.of(A, B), "kit.zip", 31, true, false);
        assertThat(kit.omitted()).isEmpty();
        Map<String, byte[]> zip = entries(kit);

        assertThat(zip.keySet()).containsExactly(
                "MusallahBoard/",
                "MusallahBoard/setup-aaaaaaaa.mbu", "MusallahBoard/content-aaaaaaaa.mbu",
                "MusallahBoard/setup-bbbbbbbb.mbu", "MusallahBoard/content-bbbbbbbb.mbu",
                "MusallahBoard/musallahboard-app-2.1.0.mbu", "MusallahBoard/musallahboard-agent-0.5.0-arm64.mbu");
        assertThat(AgentPackageRules.unzip(zip.get("MusallahBoard/content-bbbbbbbb.mbu")).get("mbu.json"))
                .asString().isEqualTo("content " + B.getId());
        verify(content).build(A.getId(), 31);
        verify(setup).build(A.getId(), false);
        assertThat(Files.exists(kit.file())).as("deleted once sent").isFalse();
    }

    @Test
    void setupFilesOnlyWhenAskedFor() throws Exception {
        when(content.build(any(), anyInt())).thenReturn(pkg("c"));
        releasesAvailable();

        Map<String, byte[]> zip = entries(service.build(List.of(A), "kit.zip", 14, false, true));

        assertThat(zip.keySet()).noneMatch(n -> n.contains("setup-"));
        verify(setup, never()).build(any(), anyBoolean());
    }

    /** No GitHub: the kit is still made, says what it lacks, and the boards get their content. */
    @Test
    void unreachableReleasesAreLeftOutWithANotice() throws Exception {
        when(content.build(any(), anyInt())).thenReturn(pkg("c"));
        when(releases.latest(any())).thenReturn(new ReleaseChannelCache.Lookup(null, "could not fetch: timeout"));

        UsbKitService.Kit kit = service.build(List.of(A), "kit.zip", 14, false, false);

        assertThat(kit.omitted()).containsExactly("board app: could not fetch: timeout", "agent: could not fetch: timeout");
        assertThat(entries(kit).keySet()).containsExactly("MusallahBoard/", "MusallahBoard/content-aaaaaaaa.mbu");
    }

    /** A poster that cannot be fetched must not leave a rotated claim secret in a kit nobody gets. */
    @Test
    void contentIsBuiltBeforeTheSetupFile() throws Exception {
        when(content.build(any(), anyInt())).thenReturn(pkg("c"));
        when(setup.build(any(), anyBoolean())).thenReturn(pkg("s"));
        releasesAvailable();

        entries(service.build(List.of(A), "kit.zip", 14, true, true));

        InOrder order = inOrder(content, setup);
        order.verify(content).build(A.getId(), 14);
        order.verify(setup).build(A.getId(), true);
    }

    @Test
    void badDaysAndNoBoardsAreRefused() {
        assertThatThrownBy(() -> service.build(List.of(A), "kit.zip", 93, false, false))
                .isInstanceOfSatisfying(ApiResponseException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.build(List.of(), "kit.zip", 14, false, false))
                .isInstanceOfSatisfying(ApiResponseException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void aFailureLeavesNoTempFileBehind() throws Exception {
        when(content.build(any(), anyInt())).thenThrow(new ApiResponseException(HttpStatus.BAD_GATEWAY,
                com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse.of("poster")));
        long before = countKits();

        assertThatThrownBy(() -> service.build(List.of(A), "kit.zip", 14, false, false))
                .isInstanceOf(ApiResponseException.class);
        assertThat(countKits()).isEqualTo(before);
    }

    private static long countKits() throws Exception {
        try (var files = Files.list(java.nio.file.Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(p -> p.getFileName().toString().startsWith("musallahboard-usb-kit-")).count();
        }
    }
}
