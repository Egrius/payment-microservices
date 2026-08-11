package by.egrius.api_gateway.optimization;

import by.egrius.api_gateway.context.ServiceIntegrationTestContext;
import by.egrius.api_gateway.optimization.config.BaseOptimizingTest;
import by.egrius.api_gateway.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

@ActiveProfiles("optimize")
@SpringBootTest(
        classes = ServiceIntegrationTestContext.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
public class TransferServiceOptimizationTests extends BaseOptimizingTest {

    @Autowired
    private TransferService transferService;

    @Test
    void get10Latest_Test() {
        transferService.get_10_LatestTransfersByUser(UUID.fromString("ae4edaa1-3b0d-451d-9f1e-2cecdbff4573"));
    }

    @Test
    void getSenderLeaderboard() {
        transferService.getSenderLeaderboard(10,30)
                .forEach(System.out::println);
    }
}