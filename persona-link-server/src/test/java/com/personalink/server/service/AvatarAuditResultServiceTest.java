package com.personalink.server.service;

import com.personalink.server.entity.AvatarAuditResultEntity;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AvatarAuditResultServiceTest {
    @Test
    void duplicateKeepsFirstDecisionAndStorageFailureIsNotAcknowledged() {
        AvatarAuditResultService service = spy(new AvatarAuditResultService());
        doThrow(new DuplicateKeyException("trace already stored")).when(service).save(any(AvatarAuditResultEntity.class));
        doReturn(false).when(service).findResult("trace");
        assertFalse(service.record("trace", true));

        doThrow(new IllegalStateException("database unavailable")).when(service).save(any(AvatarAuditResultEntity.class));
        assertThrows(IllegalStateException.class, () -> service.record("trace", true));
    }

    @Test
    void firstDecisionIsStoredBeforeRead() {
        AvatarAuditResultService service = spy(new AvatarAuditResultService());
        doReturn(true).when(service).save(any(AvatarAuditResultEntity.class));
        doReturn(true).when(service).findResult("trace");
        assertTrue(service.record("trace", true));
        var ordered = inOrder(service);
        ordered.verify(service).save(any(AvatarAuditResultEntity.class));
        ordered.verify(service).findResult("trace");
    }
}
