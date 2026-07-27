package kh.edu.istad.ite.features.payment;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.payment.dto.BakongSettingRequest;
import kh.edu.istad.ite.features.payment.dto.BakongSettingResponse;
import kh.edu.istad.ite.features.payment.dto.KhqrPreviewRequest;
import kh.edu.istad.ite.features.payment.dto.KhqrResponse;
import kh.edu.istad.ite.features.payment.service.BakongSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/businesses/payment-settings/bakong")
@RequiredArgsConstructor
public class BakongSettingController {

    private final BakongSettingService bakongSettingService;

    @GetMapping
    public BakongSettingResponse getMySetting() {
        return bakongSettingService.getMySetting();
    }

    @PutMapping
    public BakongSettingResponse saveMySetting(@Valid @RequestBody BakongSettingRequest request) {
        return bakongSettingService.saveMySetting(request);
    }

    @PatchMapping("/activate")
    public BakongSettingResponse activate() {
        return bakongSettingService.activate();
    }

    @PatchMapping("/deactivate")
    public BakongSettingResponse deactivate() {
        return bakongSettingService.deactivate();
    }

    @PostMapping("/preview-qr")
    public KhqrResponse preview(@Valid @RequestBody KhqrPreviewRequest request) {
        return bakongSettingService.preview(request);
    }
}
