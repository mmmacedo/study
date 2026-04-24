package com.study.auth.dto;

import java.util.List;

public record IntrospectResponse(
        String sub,
        String preferredUsername,
        List<String> roles,
        long exp
) {
}
