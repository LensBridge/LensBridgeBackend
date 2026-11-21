package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.config.UploadProperties;
import com.ibrasoft.lensbridge.exception.DailyLimitExceededException;
import com.ibrasoft.lensbridge.exception.FileSizeLimitExceededException;
import com.ibrasoft.lensbridge.exception.InvalidContentTypeException;
import com.ibrasoft.lensbridge.model.auth.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.unit.DataSize;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UploadLimitsServiceTest {

    private final UploadProperties properties = new UploadProperties();
    private final UploadService uploadService = mock(UploadService.class);
    private final UploadLimitsService service = new UploadLimitsService(properties, uploadService);

    private static Authentication holding(String... authorities) {
        return new UsernamePasswordAuthenticationToken("u", "p",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    // ── getHighestRole ────────────────────────────────────────────────────────

    @Test
    void anAdminWhoIsAlsoABoardEditorStillGetsAdminLimits() {
        // BOARD_EDITOR sits after ADMIN in the enum, which is what the old max-ordinal logic tripped on
        assertThat(service.getHighestRole(holding("ROLE_USER", "ROLE_ADMIN", "ROLE_BOARD_EDITOR")))
                .isEqualTo(Role.ADMIN);
        assertThat(service.getHighestRole(holding("ROLE_ADMIN", "ROLE_BOARD_ADMIN"))).isEqualTo(Role.ADMIN);
    }

    @Test
    void rootBeatsEverything() {
        assertThat(service.getHighestRole(holding("ROLE_ADMIN", "ROLE_BOARD_ADMIN", "ROLE_ROOT")))
                .isEqualTo(Role.ROOT);
    }

    @Test
    void boardRolesAloneMeanUserLimits() {
        assertThat(service.getHighestRole(holding("ROLE_USER", "ROLE_BOARD_VIEWER"))).isEqualTo(Role.USER);
        assertThat(service.getHighestRole(holding("ROLE_BOARD_ADMIN"))).isEqualTo(Role.USER);
    }

    @Test
    void permissionAuthoritiesAndUnknownOnesAreIgnored() {
        assertThat(service.getHighestRole(holding("ROLE_USER", "media:upload:moderate", "ROLE_WHATEVER")))
                .isEqualTo(Role.USER);
    }

    @Test
    void noAuthenticationMeansUser() {
        assertThat(service.getHighestRole(null)).isEqualTo(Role.USER);
    }

    // ── validateUpload ────────────────────────────────────────────────────────

    private void configure() {
        properties.setAllowedFileTypes(List.of("image/jpeg", "image/png"));
        properties.setGlobalMaxSize(DataSize.ofMegabytes(10));
        properties.setMaxSize(Map.of("user", DataSize.ofMegabytes(5), "admin", DataSize.ofMegabytes(50)));
        properties.setGlobalDailyLimit(3);
        properties.setDailyLimit(Map.of("user", 3, "admin", 100));
    }

    @Test
    void validateUploadAppliesTheLimitsOfTheGivenRole() {
        configure();
        UUID user = UUID.randomUUID();
        when(uploadService.hasReachedDailyLimit(user, 100)).thenReturn(false);

        long sixMb = 6L * 1024 * 1024;
        service.validateUpload(user, Role.ADMIN, sixMb, "image/jpeg");
        assertThatThrownBy(() -> service.validateUpload(user, Role.USER, sixMb, "image/jpeg"))
                .isInstanceOf(FileSizeLimitExceededException.class);
    }

    @Test
    void validateUploadRejectsAContentTypeThatIsNotAllowed() {
        configure();

        assertThatThrownBy(() -> service.validateUpload(UUID.randomUUID(), Role.USER, 1, "image/gif"))
                .isInstanceOf(InvalidContentTypeException.class);
    }

    @Test
    void theDailyLimitErrorCarriesTheRoleThatApplied() {
        configure();
        UUID user = UUID.randomUUID();
        when(uploadService.hasReachedDailyLimit(user, 100)).thenReturn(true);
        when(uploadService.countUploadsToday(user)).thenReturn(100L);

        assertThatThrownBy(() -> service.validateUpload(user, Role.ADMIN, 1, "image/png"))
                .isInstanceOfSatisfying(DailyLimitExceededException.class, e -> {
                    assertThat(e.getRole()).isEqualTo("admin");
                    assertThat(e.getLimit()).isEqualTo(100);
                    assertThat(e.getCurrent()).isEqualTo(100);
                });
    }
}
