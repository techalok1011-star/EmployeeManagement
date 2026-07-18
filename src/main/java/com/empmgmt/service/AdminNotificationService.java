package com.empmgmt.service;

import com.empmgmt.dto.AdminNotificationDTO;
import com.empmgmt.entity.AdminNotification;
import com.empmgmt.repository.AdminNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminNotificationService {

    private final AdminNotificationRepository notificationRepository;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        return notificationRepository.countByReadFalse();
    }

    @Transactional(readOnly = true)
    public List<AdminNotificationDTO.Response> getAllNotifications() {
        return notificationRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public void markAllRead() {
        notificationRepository.markAllRead();
    }

    private AdminNotificationDTO.Response toResponse(AdminNotification n) {
        return AdminNotificationDTO.Response.builder()
                .id(n.getId())
                .type(n.getType().name())
                .message(n.getMessage())
                .partyName(n.getPartyName())
                .amount(n.getAmount())
                .triggeredBy(n.getTriggeredBy())
                .triggeredByRole(n.getTriggeredByRole())
                .sourceType(n.getSourceType())
                .sourceId(n.getSourceId())
                .read(n.isRead())
                .createdAt(n.getCreatedAt() != null ? n.getCreatedAt().format(FMT) : "")
                .build();
    }
}
