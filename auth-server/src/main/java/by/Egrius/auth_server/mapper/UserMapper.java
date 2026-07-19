package by.Egrius.auth_server.mapper;

import by.Egrius.auth_server.dto.user.UserReadDto;
import by.Egrius.auth_server.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "publicId", target = "publicId")
    UserReadDto toReadDto(User user);
}