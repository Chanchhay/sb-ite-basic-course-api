package kh.edu.istad.ite.features.admin.controller;

import kh.edu.istad.ite.features.admin.dto.response.BusinessChannelResponse;
import kh.edu.istad.ite.features.admin.service.AdminChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/channels")
@RequiredArgsConstructor
public class AdminChannelController {

    private final AdminChannelService adminChannelService;

    @GetMapping
    public List<BusinessChannelResponse> findAllChannels() {
        return adminChannelService.findAllChannels();
    }
}
