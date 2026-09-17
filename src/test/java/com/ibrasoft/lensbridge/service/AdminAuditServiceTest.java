package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.audit.AuditEventDto;
import com.ibrasoft.lensbridge.model.audit.AuditAction;
import com.ibrasoft.lensbridge.model.audit.AuditEntityType;
import com.ibrasoft.lensbridge.model.audit.AuditEvent;
import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.repository.audit.AuditEventRepository;
import com.ibrasoft.lensbridge.repository.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminAuditService service;

    private User newAdmin() {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setFirstName("Jane");
        u.setLastName("Admin");
        u.setEmail("jane@example.com");
        return u;
    }

    private AuditEvent newEvent(User admin) {
        return AuditEvent.builder()
                .id(UUID.randomUUID())
                .action(AuditAction.APPROVE_UPLOAD)
                .admin(admin)
                .targetEntityType(AuditEntityType.UPLOAD)
                .targetEntityId(UUID.randomUUID())
                .ipAddress("127.0.0.1")
                .userAgent("JUnit")
                .build();
    }

    // ---- logAuditEvent(AuditEvent) ----

    @Test
    void logAuditEventSetsTimestampAndPersists() {
        User admin = newAdmin();
        AuditEvent event = newEvent(admin);
        when(auditEventRepository.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        AuditEvent saved = service.logAuditEvent(event);

        assertThat(saved).isNotNull();
        assertThat(saved.getTimestamp()).isNotNull();
        assertThat(saved.getTimestamp()).isAfterOrEqualTo(before);
        verify(auditEventRepository).save(event);
    }

    @Test
    void logAuditEventReturnsNullWhenRepositoryThrows() {
        User admin = newAdmin();
        AuditEvent event = newEvent(admin);
        when(auditEventRepository.save(any(AuditEvent.class)))
                .thenThrow(new RuntimeException("db down"));

        AuditEvent result = service.logAuditEvent(event);

        assertThat(result).isNull();
    }

    // ---- logAuditEvent(String, AuditAction, String, UUID, String) ----

    @Test
    void logAuditEventByEmailBuildsEventFromLookedUpAdmin() {
        User admin = newAdmin();
        UUID entityId = UUID.randomUUID();
        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(admin));
        when(auditEventRepository.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditEvent result = service.logAuditEvent(
                "jane@example.com", AuditAction.DELETE_UPLOAD, "upload", entityId, "10.0.0.1");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        AuditEvent built = captor.getValue();
        assertThat(built.getAdmin()).isSameAs(admin);
        assertThat(built.getAction()).isEqualTo(AuditAction.DELETE_UPLOAD);
        assertThat(built.getTargetEntityType()).isEqualTo(AuditEntityType.UPLOAD);
        assertThat(built.getTargetEntityId()).isEqualTo(entityId);
        assertThat(built.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(result).isNotNull();
    }

    @Test
    void logAuditEventByEmailThrowsWhenAdminNotFound() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.logAuditEvent("missing@example.com",
                AuditAction.APPROVE_UPLOAD, "upload", UUID.randomUUID(), "1.1.1.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing@example.com");
        verify(auditEventRepository, never()).save(any());
    }

    @Test
    void logAuditEventByEmailMapsEntityTypeStrings() {
        User admin = newAdmin();
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(admin));
        when(auditEventRepository.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);

        service.logAuditEvent("e", AuditAction.UPDATE_USER, "user", UUID.randomUUID(), "ip");
        service.logAuditEvent("e", AuditAction.UPDATE_USER, "poster", UUID.randomUUID(), "ip");
        service.logAuditEvent("e", AuditAction.UPDATE_USER, "calendarevent", UUID.randomUUID(), "ip");
        service.logAuditEvent("e", AuditAction.UPDATE_USER, "boardevent", UUID.randomUUID(), "ip");
        service.logAuditEvent("e", AuditAction.UPDATE_USER, "musallah board", UUID.randomUUID(), "ip");
        service.logAuditEvent("e", AuditAction.UPDATE_USER, "unknown", UUID.randomUUID(), "ip");
        service.logAuditEvent("e", AuditAction.UPDATE_USER, null, UUID.randomUUID(), "ip");

        verify(auditEventRepository, times(7)).save(captor.capture());
        List<AuditEvent> events = captor.getAllValues();
        assertThat(events.get(0).getTargetEntityType()).isEqualTo(AuditEntityType.USER);
        assertThat(events.get(1).getTargetEntityType()).isEqualTo(AuditEntityType.MUSALLAH_BOARD);
        assertThat(events.get(2).getTargetEntityType()).isEqualTo(AuditEntityType.MUSALLAH_BOARD);
        assertThat(events.get(3).getTargetEntityType()).isEqualTo(AuditEntityType.MUSALLAH_BOARD);
        assertThat(events.get(4).getTargetEntityType()).isEqualTo(AuditEntityType.MUSALLAH_BOARD);
        assertThat(events.get(5).getTargetEntityType()).isEqualTo(AuditEntityType.EVENT);
        assertThat(events.get(6).getTargetEntityType()).isEqualTo(AuditEntityType.EVENT);
    }

    // ---- queries ----

    @Test
    void getAllAuditEventsMapsToDtoPage() {
        User admin = newAdmin();
        AuditEvent event = newEvent(admin);
        Pageable pageable = PageRequest.of(0, 10);
        when(auditEventRepository.findAllByOrderByTimestampDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(event)));

        Page<AuditEventDto> page = service.getAllAuditEvents(pageable);

        assertThat(page.getContent()).hasSize(1);
        AuditEventDto dto = page.getContent().get(0);
        assertThat(dto.getId()).isEqualTo(event.getId());
        assertThat(dto.getAction()).isEqualTo(AuditAction.APPROVE_UPLOAD);
        assertThat(dto.getAdminId()).isEqualTo(admin.getId());
        assertThat(dto.getAdminName()).isEqualTo("Jane Admin");
        assertThat(dto.getAdminEmail()).isEqualTo("jane@example.com");
        assertThat(dto.getTargetEntityType()).isEqualTo(AuditEntityType.UPLOAD);
        assertThat(dto.getTargetEntityId()).isEqualTo(event.getTargetEntityId());
        assertThat(dto.getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(dto.getUserAgent()).isEqualTo("JUnit");
    }

    @Test
    void getAuditEventsByAdminMapsList() {
        User admin = newAdmin();
        when(auditEventRepository.findByAdminIdOrderByTimestampDesc(admin.getId()))
                .thenReturn(List.of(newEvent(admin), newEvent(admin)));

        List<AuditEventDto> dtos = service.getAuditEventsByAdmin(admin.getId());

        assertThat(dtos).hasSize(2);
        assertThat(dtos).allSatisfy(d -> assertThat(d.getAdminId()).isEqualTo(admin.getId()));
    }

    @Test
    void getAuditEventsByEntityResolvesEntityType() {
        User admin = newAdmin();
        UUID entityId = UUID.randomUUID();
        when(auditEventRepository.findByTargetEntityTypeAndTargetEntityIdOrderByTimestampDesc(
                AuditEntityType.UPLOAD, entityId)).thenReturn(List.of(newEvent(admin)));

        List<AuditEventDto> dtos = service.getAuditEventsByEntity("upload", entityId);

        assertThat(dtos).hasSize(1);
        verify(auditEventRepository).findByTargetEntityTypeAndTargetEntityIdOrderByTimestampDesc(
                AuditEntityType.UPLOAD, entityId);
    }

    @Test
    void getAuditEventsByActionMapsPage() {
        User admin = newAdmin();
        Pageable pageable = PageRequest.of(0, 5);
        when(auditEventRepository.findByActionOrderByTimestampDesc(AuditAction.DELETE_UPLOAD, pageable))
                .thenReturn(new PageImpl<>(List.of(newEvent(admin))));

        Page<AuditEventDto> page = service.getAuditEventsByAction(AuditAction.DELETE_UPLOAD, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getAuditEventsByDateRangeMapsPage() {
        User admin = newAdmin();
        Instant start = Instant.now().minusSeconds(3600);
        Instant end = Instant.now();
        Pageable pageable = PageRequest.of(0, 5);
        when(auditEventRepository.findByTimestampBetweenOrderByTimestampDesc(start, end, pageable))
                .thenReturn(new PageImpl<>(List.of(newEvent(admin))));

        Page<AuditEventDto> page = service.getAuditEventsByDateRange(start, end, pageable);

        assertThat(page.getContent()).hasSize(1);
        verify(auditEventRepository).findByTimestampBetweenOrderByTimestampDesc(start, end, pageable);
    }

    @Test
    void getFailedOperationsReturnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 5);

        Page<AuditEventDto> page = service.getFailedOperations(pageable);

        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getContent()).isEmpty();
        verify(auditEventRepository, never()).findAllByOrderByTimestampDesc(any());
    }

    // ---- DTO mapping edge: null admin ----

    @Test
    void toDtoHandlesNullAdminGracefully() {
        AuditEvent event = AuditEvent.builder()
                .id(UUID.randomUUID())
                .action(AuditAction.SYSTEM_MAINTENANCE)
                .timestamp(Instant.now())
                .admin(null)
                .targetEntityType(AuditEntityType.EVENT)
                .build();
        when(auditEventRepository.findAllByOrderByTimestampDesc(any()))
                .thenReturn(new PageImpl<>(List.of(event)));

        AuditEventDto dto = service.getAllAuditEvents(PageRequest.of(0, 1)).getContent().get(0);

        assertThat(dto.getAdminId()).isNull();
        assertThat(dto.getAdminName()).isNull();
        assertThat(dto.getAdminEmail()).isNull();
    }

    // ---- statistics ----

    @Test
    void getOperationCountByAdminDelegatesToRepository() {
        UUID adminId = UUID.randomUUID();
        when(auditEventRepository.countByAdminId(adminId)).thenReturn(7L);

        assertThat(service.getOperationCountByAdmin(adminId)).isEqualTo(7L);
    }

    @Test
    void getOperationCountByActionDelegatesToRepository() {
        when(auditEventRepository.countByAction(AuditAction.APPROVE_UPLOAD)).thenReturn(3L);

        assertThat(service.getOperationCountByAction(AuditAction.APPROVE_UPLOAD)).isEqualTo(3L);
    }
}
