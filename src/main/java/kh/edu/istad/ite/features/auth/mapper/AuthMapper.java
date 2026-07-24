package kh.edu.istad.ite.features.auth.mapper;

import kh.edu.istad.ite.features.auth.dto.RegisterResponse;
import org.keycloak.representations.idm.UserRepresentation;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class AuthMapper {
    private static final String PHONE_NUMBER_ATTRIBUTE = "phone_number";
    private static final String GENDER_ATTRIBUTE = "gender";

    public RegisterResponse toRegisterResponse(UserRepresentation userRepresentation, String role){
        return RegisterResponse.builder()
                .id(userRepresentation.getId())
                .username(userRepresentation.getUsername())
                .email(userRepresentation.getEmail())
                .firstName(userRepresentation.getFirstName())
                .lastName(userRepresentation.getLastName())
                .phoneNumber(userRepresentation.firstAttribute(PHONE_NUMBER_ATTRIBUTE))
                .gender(userRepresentation.firstAttribute(GENDER_ATTRIBUTE))
                .role(role)
                .build();
    }
}
