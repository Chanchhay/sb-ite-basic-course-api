package kh.edu.istad.ite.features.admin.service;

import kh.edu.istad.ite.features.admin.dto.response.BusinessChannelResponse;

import java.util.List;

public interface AdminChannelService {

    /** One row per shop, showing what it has connected and published. */
    List<BusinessChannelResponse> findAllChannels();
}
