package com.example;

import lombok.Data;
import lombok.Builder;
import lombok.NonNull;

@Data
@Builder
public class User {
    @NonNull
    private String name;
    private int age;
    private String email;
}
