package com.personalink.server.service.impl;
import com.personalink.server.dto.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdConfigValidationTest {
    @Test
    void operatorsAndViewersCannotSave() {
        var service = new AdConfigServiceImpl(null, new com.fasterxml.jackson.databind.ObjectMapper());
        for (int role : new int[]{2, 3}) {
            var operator = new com.personalink.server.entity.AdminAccountEntity();
            operator.setRoleType(role);
            assertThrows(com.personalink.server.exception.BusinessException.class,
                    () -> service.saveConfig(new AdConfigSaveRequest(false, "", 1), operator));
        }
    }
    @Test
    void validatesConfigurationAndAdEvents() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(new AdConfigSaveRequest(false, "", 1)).isEmpty());
            assertTrue(validator.validate(new AdConfigSaveRequest(true, " adunit-abc123 ", 2)).isEmpty());
            assertFalse(validator.validate(new AdConfigSaveRequest(true, "", 1)).isEmpty());
            assertFalse(validator.validate(new AdConfigSaveRequest(true, "https://bad", 1)).isEmpty());
            assertFalse(validator.validate(new AdConfigSaveRequest(false, "", 3)).isEmpty());
            assertFalse(validator.validate(new AdResultRequest("invalid", 1, null)).isEmpty());
            assertFalse(validator.validate(new AdResultRequest("00000000-0000-0000-0000-000000000001", 2, null)).isEmpty());
            assertFalse(validator.validate(new AdResultRequest("00000000-0000-0000-0000-000000000001", 2, -1)).isEmpty());
        }
    }
}
