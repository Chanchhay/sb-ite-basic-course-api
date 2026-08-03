package kh.edu.istad.ite.features.business.mapper;

import kh.edu.istad.ite.features.admin.dto.PlatformRoleResponse;
import kh.edu.istad.ite.features.business.dto.BusinessRoleResponse;
import org.keycloak.representations.idm.RoleRepresentation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    @Mapping(target = "id", source = "role.id")
    @Mapping(target = "name", expression = "java(role.getDescription() != null ? role.getDescription() : role.getName())")
    @Mapping(target = "permissions", source = "permissions")
    BusinessRoleResponse toBusinessRoleResponse(RoleRepresentation role, List<String> permissions);

    @Mapping(target = "id", source = "role.id")
    @Mapping(target = "name", expression = "java(role.getDescription() != null ? role.getDescription() : role.getName())")
    @Mapping(target = "permissions", source = "permissions")
    PlatformRoleResponse toPlatformRoleResponse(RoleRepresentation role, List<String> permissions);
}
