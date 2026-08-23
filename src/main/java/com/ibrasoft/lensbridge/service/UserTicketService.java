package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.dto.ticket.BoardingPassView;
import com.ibrasoft.lensbridge.model.minbar.BoardEvent;
import com.ibrasoft.lensbridge.repository.sql.BoardEventRepository;
import com.ibrasoft.tcketmanagebackend.model.ticket.Ticket;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketQRData;
import com.ibrasoft.tcketmanagebackend.model.ticket.TicketStatus;
import com.ibrasoft.tcketmanagebackend.service.CryptoService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "tcketmanage", name = "enabled")
@RequiredArgsConstructor
@Slf4j
public class UserTicketService {

    private final BoardEventRepository boardEventRepository;
    private final CryptoService cryptoService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<BoardingPassView> getTicketsForUser(UUID userId) {
        List<Ticket> tickets = entityManager
                .createQuery("SELECT t FROM Ticket t WHERE t.holderRef = :ref AND t.status = :status", Ticket.class)
                .setParameter("ref", userId.toString())
                .setParameter("status", TicketStatus.ACTIVE)
                .getResultList();

        if (tickets.isEmpty()) {
            return List.of();
        }

        Set<UUID> tcketEventIds = tickets.stream()
                .filter(t -> t.getEvent() != null)
                .map(t -> t.getEvent().getId())
                .collect(Collectors.toSet());

        Map<UUID, BoardEvent> boardEventByTcketEventId = boardEventRepository
                .findByEventIdIn(tcketEventIds).stream()
                .filter(be -> be.getEvent() != null)
                .collect(Collectors.toMap(
                        be -> be.getEvent().getId(),
                        be -> be,
                        (a, b) -> a));

        return tickets.stream()
                .map(ticket -> toBoardingPass(ticket, boardEventByTcketEventId))
                .sorted(Comparator.comparing(BoardingPassView::getEventDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private BoardingPassView toBoardingPass(Ticket ticket, Map<UUID, BoardEvent> boardEventMap) {
        UUID tcketEventId = ticket.getEvent() != null ? ticket.getEvent().getId() : null;
        BoardEvent boardEvent = tcketEventId != null ? boardEventMap.get(tcketEventId) : null;

        String eventTitle = boardEvent != null ? boardEvent.getName() :
                (ticket.getEvent() != null ? ticket.getEvent().getName() : "Unknown Event");
        String location = boardEvent != null && boardEvent.getLocation() != null ? boardEvent.getLocation() :
                (ticket.getEvent() != null ? ticket.getEvent().getLocation() : null);

        String eventDate = null;
        String startTime = null;
        if (boardEvent != null) {
            eventDate = DateTimeFormatter.ISO_LOCAL_DATE
                    .format(boardEvent.getStartTime().atOffset(java.time.ZoneOffset.UTC));
            startTime = DateTimeFormatter.ofPattern("HH:mm")
                    .format(boardEvent.getStartTime().atOffset(java.time.ZoneOffset.UTC));
        } else if (ticket.getEvent() != null && ticket.getEvent().getTime() != null) {
            OffsetDateTime t = ticket.getEvent().getTime();
            eventDate = t.toLocalDate().toString();
            startTime = t.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
        }

        String qrPayload = null;
        try {
            qrPayload = cryptoService.sign(TicketQRData.fromTicket(ticket));
        } catch (Exception e) {
            log.warn("Failed to sign QR payload for ticket {}", ticket.getID(), e);
        }

        return BoardingPassView.builder()
                .ticketId(ticket.getID())
                .eventId(tcketEventId)
                .eventTitle(eventTitle)
                .eventDate(eventDate)
                .startTime(startTime)
                .location(location)
                .ticketType(ticket.getTicketType() != null ? ticket.getTicketType().getName() : null)
                .attendeeName(ticket.getFirstName() + " " + ticket.getLastName())
                .qrPayload(qrPayload)
                .status(ticket.getStatus().name())
                .build();
    }
}
