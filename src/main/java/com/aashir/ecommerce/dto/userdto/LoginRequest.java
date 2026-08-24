package com.aashir.ecommerce.dto.userdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginRequest {

    @NotBlank(message = "name must be required")
    private String email;

    @NotBlank(message = "Passward required")
    @Size(min = 6,message = "lenght caanot short")
    private String password;

    LoginRequest(){

    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
