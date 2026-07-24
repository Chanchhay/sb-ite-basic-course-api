package kh.edu.istad.ite.features.auth.dto;

import lombok.Getter;

@Getter
public enum GenderOptions {
    MALE("Male"),
    FEMALE("Female"),
    OTHER("Other"),
    UNSPECIFIED("Unspecified");

    private final String gender;

    GenderOptions(String gender) {
        this.gender = gender;
    }
}
