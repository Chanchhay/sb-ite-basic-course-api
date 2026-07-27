package kh.edu.istad.ite.features.customerdisplay;

import kh.edu.istad.ite.features.customerdisplay.service.CustomerDisplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/customer-display")
@RequiredArgsConstructor
public class CustomerDisplayController {
    private final CustomerDisplayService customerDisplayService;

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/{terminalId}/publish")
    public void publish(
            @PathVariable UUID businessId,
            @PathVariable String terminalId,
            @RequestBody Object state
    ) {
        customerDisplayService.publish(businessId, terminalId, state);
    }
}
