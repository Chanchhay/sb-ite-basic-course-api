package kh.edu.istad.ite.features.user.mapper;

import kh.edu.istad.ite.features.user.dto.UpdateUserProfileRequest;
import kh.edu.istad.ite.features.user.dto.UserProfileResponse;
import kh.edu.istad.ite.features.user.dto.StaffResponse;
import kh.edu.istad.ite.features.user.entity.UserProfile;
import org.keycloak.representations.idm.UserRepresentation;
import org.mapstruct.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


import kh.edu.istad.ite.features.minio.MinioService;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class UserProfileMapper {
    private static final String PHONE_NUMBER_ATTRIBUTE = "phone_number";
    private static final String GENDER_ATTRIBUTE = "gender";

    @Autowired
    protected MinioService minioService;

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "profilePicture", ignore = true)
    public abstract void mapUpdateUserProfileRequestToUserProfile(
            UpdateUserProfileRequest updateUserProfileRequest,
            @MappingTarget UserProfile userProfile
    );

    public void mapUpdateUserProfileRequestToUserRepresentation(
            UpdateUserProfileRequest updateUserProfileRequest,
            @MappingTarget UserRepresentation userRepresentation
    ) {
        if (updateUserProfileRequest.firstName() != null)
            userRepresentation.setFirstName(updateUserProfileRequest.firstName());

        if (updateUserProfileRequest.lastName() != null)
            userRepresentation.setLastName(updateUserProfileRequest.lastName());

        if (updateUserProfileRequest.phoneNumber() != null
                || updateUserProfileRequest.gender() != null) {
            Map<String, List<String>> attributes = new HashMap<>();
            if (userRepresentation.getAttributes() != null) {
                attributes.putAll(userRepresentation.getAttributes());
            }
            putAttributeIfPresent(attributes, PHONE_NUMBER_ATTRIBUTE, updateUserProfileRequest.phoneNumber());
            putAttributeIfPresent(attributes, GENDER_ATTRIBUTE, updateUserProfileRequest.gender());
            userRepresentation.setAttributes(attributes);
        }
    }

    public UserProfileResponse toUserProfileResponse(UserRepresentation userRepresentation, UserProfile userProfile, String role) {
        String profilePictureUrl = null;
        if (userProfile.getProfilePicture() != null && !userProfile.getProfilePicture().isBlank()) {
            profilePictureUrl = userProfile.getProfilePicture().startsWith("http://") || userProfile.getProfilePicture().startsWith("https://")
                    ? userProfile.getProfilePicture()
                    : minioService.getPublicUrl(userProfile.getProfilePicture());
        }

        return UserProfileResponse.builder()
                .userId(userRepresentation.getId())
                .username(userRepresentation.getUsername())
                .email(userRepresentation.getEmail())
                .firstName(userRepresentation.getFirstName())
                .lastName(userRepresentation.getLastName())
                .phoneNumber(firstAttribute(userRepresentation, PHONE_NUMBER_ATTRIBUTE, userProfile.getPhoneNumber()))
                .gender(userProfile.getGender())
                .role(role)
                .address(userProfile.getAddress())
                .profilePicture(profilePictureUrl)
                .build();
    }

    @Mapping(target = "id", source = "userProfile.userId")
    @Mapping(target = "username", source = "userRepresentation.username")
    @Mapping(target = "email", source = "userRepresentation.email")
    @Mapping(target = "firstName", source = "userRepresentation.firstName")
    @Mapping(target = "lastName", source = "userRepresentation.lastName")
    @Mapping(target = "phoneNumber", source = "userProfile.phoneNumber")
    @Mapping(target = "gender", source = "userProfile.gender")
    @Mapping(target = "status", source = "userProfile.userStatus")
    @Mapping(target = "roleId", source = "roleId")
    public abstract StaffResponse toStaffResponse(UserRepresentation userRepresentation, UserProfile userProfile, String roleId);

    private void putAttributeIfPresent(Map<String, List<String>> attributes, String name, String value) {
        if (value != null) {
            attributes.put(name, List.of(value));
        }
    }

    private String firstAttribute(UserRepresentation userRepresentation, String name, String fallback) {
        if (userRepresentation.getAttributes() == null || userRepresentation.getAttributes().get(name) == null) {
            return fallback;
        }

        List<String> values = userRepresentation.getAttributes().get(name);
        return values.isEmpty() ? fallback : values.getFirst();
    }

}
