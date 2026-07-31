package kh.edu.istad.ite.features.channel.mapper;

import kh.edu.istad.ite.features.channel.dto.ItemChannelResponse;
import kh.edu.istad.ite.features.channel.entity.ItemChannel;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ItemChannelMapper {
    ItemChannelResponse toResponse(ItemChannel itemChannel);
}
