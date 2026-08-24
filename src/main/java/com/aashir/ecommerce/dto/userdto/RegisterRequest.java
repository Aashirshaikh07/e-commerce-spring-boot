package com.aashir.ecommerce.dto.userdto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "name must be required")
    private String name;

    @Email(message = "Email must be valid")
    @NotBlank(message = "name must be required")
    private String email;

    @NotBlank
    @Size(min = 10,message = "phone number cannot be less than 10")
    String phone;

    @Size(min = 6,message ="minimum 6 character required" )
    @NotBlank(message = "name must be required")
    private String password;

    @Size(min = 6,message ="minimum 6 character required" )
    @NotBlank(message = "name must be required")
    private String confirmPassword;

    RegisterRequest(){

    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
