package kh.edu.istad.ite.features.social;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.social.dto.TelegramBotSettingRequest;
import kh.edu.istad.ite.features.social.dto.TelegramBotSettingResponse;
import kh.edu.istad.ite.features.social.service.BusinessTelegramBotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/businesses/social-settings/telegram-bot")
@RequiredArgsConstructor
public class BusinessTelegramBotController {

    private final BusinessTelegramBotService businessTelegramBotService;

    @GetMapping
    public TelegramBotSettingResponse getMySetting() {
        return businessTelegramBotService.getMySetting();
    }

    @PutMapping
    public TelegramBotSettingResponse connect(@Valid @RequestBody TelegramBotSettingRequest request) {
        return businessTelegramBotService.connect(request);
    }

    @PatchMapping("/activate")
    public TelegramBotSettingResponse activate() {
        return businessTelegramBotService.activate();
    }

    @PatchMapping("/deactivate")
    public TelegramBotSettingResponse deactivate() {
        return businessTelegramBotService.deactivate();
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping
    public void disconnect() {
        businessTelegramBotService.disconnect();
    }
}
