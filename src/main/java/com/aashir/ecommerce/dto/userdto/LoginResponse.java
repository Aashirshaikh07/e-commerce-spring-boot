package com.aashir.ecommerce.dto.userdto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter@Setter
public class LoginResponse {

    private String token;
    private String token_type;
}

