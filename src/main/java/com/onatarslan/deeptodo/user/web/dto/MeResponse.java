package com.onatarslan.deeptodo.user.web.dto;

import java.util.List;

public record MeResponse (
        String name,
        List<String> authorities
){
}
