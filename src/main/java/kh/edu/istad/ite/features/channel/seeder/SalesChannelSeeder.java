package kh.edu.istad.ite.features.channel.seeder;

import kh.edu.istad.ite.features.channel.entity.SalesChannel;
import kh.edu.istad.ite.features.channel.repository.SalesChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SalesChannelSeeder implements CommandLineRunner {

    private final SalesChannelRepository salesChannelRepository;

    @Override
    public void run(String... args) throws Exception {
        if (salesChannelRepository.count() == 0) {
            SalesChannel pos = new SalesChannel();
            pos.setName("Point of Sale");
            pos.setCode("POS");
            pos.setIsActive(true);

            SalesChannel online = new SalesChannel();
            online.setName("Online Store");
            online.setCode("ONLINE");
            online.setIsActive(true);

            salesChannelRepository.saveAll(List.of(pos, online));
        }
    }
}
