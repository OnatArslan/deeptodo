package com.onatarslan.deeptodo.user.web.dto;

import java.util.List;
import java.util.UUID;

public record MeResponse(
        UUID id,
        String email,
        String name,
        List<String> authorities
) {


}
