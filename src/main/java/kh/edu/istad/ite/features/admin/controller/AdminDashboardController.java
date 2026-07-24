package kh.edu.istad.ite.features.admin.controller;

import kh.edu.istad.ite.features.admin.dto.response.PlatformDashboardResponse;
import kh.edu.istad.ite.features.admin.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping
    public PlatformDashboardResponse getDashboard() {
        return adminDashboardService.getDashboard();
    }
}
