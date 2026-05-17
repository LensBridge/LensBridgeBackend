package com.ibrasoft.lensbridge.service.agent;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.board.Device;
import com.ibrasoft.lensbridge.model.board.EnrollmentToken;
import com.ibrasoft.lensbridge.model.board.embedded.DeviceConfig;
import com.ibrasoft.lensbridge.repository.sql.BoardConfigRepository;
import com.ibrasoft.lensbridge.repository.sql.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceEnrollmentServiceTest {

    @Mock
    private EnrollmentTokenService enrollmentTokenService;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private BoardConfigRepository boardConfigRepository;

    @InjectMocks
    private DeviceEnrollmentService service;

    private String validKeyB64;

    @BeforeEach
    void setUp() {
        validKeyB64 = Base64.getEncoder().encodeToString(new byte[32]);
        lenient().when(deviceRepository.save(any(Device.class))).thenAnswer(inv -> {
            Device d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });
    }

    private EnrollmentToken token() {
        EnrollmentToken t = new EnrollmentToken();
        t.setId(UUID.randomUUID());
        t.setDisplayName("Brothers Display");
        t.setAudience(Audience.BROTHERS);
        t.setCreatedBy("admin@example.com");
        return t;
    }

    @Test
    void enrollRejectsNonBase64PublicKey() {
        var outcome = service.enroll("tok", "!!!not-base64!!!", "host", "model", "v1", "1.2.3.4");

        assertThat(outcome).isInstanceOf(DeviceEnrollmentService.Outcome.InvalidPublicKey.class);
        verifyNoInteractions(enrollmentTokenService, deviceRepository, boardConfigRepository);
    }

    @Test
    void enrollRejectsWrongLengthPublicKey() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        var outcome = service.enroll("tok", shortKey, "host", "model", "v1", "1.2.3.4");

        assertThat(outcome).isInstanceOf(DeviceEnrollmentService.Outcome.InvalidPublicKey.class);
        assertThat(((DeviceEnrollmentService.Outcome.InvalidPublicKey) outcome).reason())
                .contains("got 16");
        verifyNoInteractions(enrollmentTokenService);
    }

    @Test
    void enrollRejectsInvalidToken() {
        when(enrollmentTokenService.consume(eq("tok"), any(UUID.class))).thenReturn(Optional.empty());

        var outcome = service.enroll("tok", validKeyB64, "host", "model", "v1", "1.2.3.4");

        assertThat(outcome).isInstanceOf(DeviceEnrollmentService.Outcome.InvalidToken.class);
        verify(deviceRepository, never()).save(any());
        verify(boardConfigRepository, never()).save(any());
    }

    @Test
    void enrollHappyPathPersistsDeviceWithDecodedKeyAndMetadata() {
        when(enrollmentTokenService.consume(anyString(), any(UUID.class))).thenReturn(Optional.of(token()));

        var outcome = service.enroll("tok", validKeyB64, "kiosk-7", "RPi4", "agent-2.0", "10.0.0.5");

        assertThat(outcome).isInstanceOf(DeviceEnrollmentService.Outcome.Ok.class);
        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        Device saved = captor.getValue();
        assertThat(saved.getDisplayName()).isEqualTo("Brothers Display (kiosk-7)");
        assertThat(saved.getAudience()).isEqualTo(Audience.BROTHERS);
        assertThat(saved.getPublicKey()).hasSize(32);
        assertThat(saved.getHardwareModel()).isEqualTo("RPi4");
        assertThat(saved.getAgentVersion()).isEqualTo("agent-2.0");
        assertThat(saved.getLastSeenIp()).isEqualTo("10.0.0.5");
        assertThat(saved.getEnrolledAt()).isNotNull();
    }

    @Test
    void enrollUsesTokenDisplayNameWhenHostnameBlank() {
        when(enrollmentTokenService.consume(anyString(), any(UUID.class))).thenReturn(Optional.of(token()));

        service.enroll("tok", validKeyB64, "   ", "model", "v1", "ip");

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        assertThat(captor.getValue().getDisplayName()).isEqualTo("Brothers Display");
    }

    @Test
    void enrollWritesBackConsumedDeviceIdAndDefaultConfig() {
        EnrollmentToken tok = token();
        when(enrollmentTokenService.consume(anyString(), any(UUID.class))).thenReturn(Optional.of(tok));

        var outcome = service.enroll("tok", validKeyB64, "host", "model", "v1", "ip");

        Device saved = ((DeviceEnrollmentService.Outcome.Ok) outcome).device();
        assertThat(tok.getConsumedByDeviceId()).isEqualTo(saved.getId());

        ArgumentCaptor<DeviceConfig> cfg = ArgumentCaptor.forClass(DeviceConfig.class);
        verify(boardConfigRepository).save(cfg.capture());
        assertThat(cfg.getValue().getDevice()).isSameAs(saved);
        assertThat(cfg.getValue().getLocation().getCity()).isEqualTo("Mississauga");
    }
}
