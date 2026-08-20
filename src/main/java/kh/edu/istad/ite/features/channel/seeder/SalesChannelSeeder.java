package kh.edu.istad.ite.features.channel.seeder;

import kh.edu.istad.ite.features.channel.entity.SalesChannel;
import kh.edu.istad.ite.features.channel.repository.SalesChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
// Off under "test": this runner hits the database on startup, and the context
// test is built to boot without one.
@Profile("!test")
public class SalesChannelSeeder implements CommandLineRunner {

    private final SalesChannelRepository salesChannelRepository;

    /**
     * The channels an order can arrive through.
     *
     * Codes match {@code OrderChannel} exactly, because that is how an order
     * says where it came from and how its channel price is then found. A
     * channel missing here is one whose orders quietly pay the business price.
     */
    private static final List<String[]> CHANNELS = List.of(
            new String[] {"POS", "Point of Sale"},
            new String[] {"WEB", "Online Store"},
            new String[] {"TELEGRAM", "Telegram"},
            new String[] {"MESSENGER", "Messenger"});

    @Override
    public void run(String... args) throws Exception {
        // Added one at a time rather than only on an empty table: a database
        // seeded before a channel existed would never get it otherwise.
        for (String[] channel : CHANNELS) {
            if (salesChannelRepository.findByCode(channel[0]).isPresent()) continue;

            SalesChannel fresh = new SalesChannel();
            fresh.setCode(channel[0]);
            fresh.setName(channel[1]);
            fresh.setIsActive(true);
            salesChannelRepository.save(fresh);
        }
    }
}
