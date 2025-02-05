package de.muzzletov.producer;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import de.muzzletov.RabbitmqContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import de.muzzletov.NeedsContainer;

@NeedsContainer(name = "rabbitmq")
@ExtendWith(RabbitmqContainer.class)
class ProducerApplicationTests {
    @BeforeAll
    static void setup() throws InterruptedException, URISyntaxException, NoSuchAlgorithmException, KeyManagementException, TimeoutException {
        final var timeout = 15000;
        var timeTaken = 0;
        var msToSleep = 50;
        var success = false;

        ConnectionFactory factory = new ConnectionFactory();
        factory.setConnectionTimeout(5000);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setUri("amqp://container-admin:container-password@localhost:5672");
        /*
        TODO: generalize
         */
        while (timeout > timeTaken) {
            try (Connection connection = factory.newConnection()) {
                success = true;
                break;
            } catch(Error | IOException | TimeoutException e) {

            }

            Thread.sleep(msToSleep);
            timeTaken += msToSleep;
        }

        System.out.println("startup took "+timeTaken+"ms");

        if (!success) throw new TimeoutException();
    }

    @Test
    void randomTest() {
        
    }

}
