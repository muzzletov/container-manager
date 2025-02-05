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
    static void setup() throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException, IOException, TimeoutException, InterruptedException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setConnectionTimeout(5000);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setUri("amqp://container-admin:container-password@localhost:5672");
        /*
         * TODO: add a probing mechanism rather so that we don't have to wait 6 seconds every time
         */
        Thread.sleep(6000);
        Connection conn = factory.newConnection();
        conn.close();
    }

    @Test
    void shouldReturnGameList() {

    }

}
