package de.muzzletov.producer;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.*;

import com.rabbitmq.client.*;
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
        factory.setConnectionTimeout(timeout);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setUri("amqp://container-admin:container-password@localhost:5672");
        /*
        TODO: generalize
         */
        while (timeout > timeTaken) {
            try (Connection connection = factory.newConnection()) {
                success = true;
                break;
            } catch (Error | IOException | TimeoutException e) {

            }

            Thread.sleep(msToSleep);
            timeTaken += msToSleep;
        }

        if (!success) throw new TimeoutException();

        System.out.println("startup took " + timeTaken + "ms");
    }

    @Test
    void randomTest() throws Exception {
        final var QUEUE_NAME = "random";
        final var MESSAGE = "HI THERE!";
        ConnectionFactory factory = new ConnectionFactory();
        // ideally you would use environment variables, to not have to maintain both sites,
        // the configuration file of docker and this file
        factory.setUri("amqp://container-admin:container-password@localhost:5672");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.queueDeclare(QUEUE_NAME, false, false, false, null);
        channel.basicPublish("", QUEUE_NAME, null, MESSAGE.getBytes());
        CompletableFuture<Void> consumeFuture = new CompletableFuture<>();

        channel.basicConsume(QUEUE_NAME, true, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
                String message = new String(body);

                if (!message.equals(MESSAGE))
                    consumeFuture.completeExceptionally(new Exception("messages do not match"));
                else {
                    System.out.println("We got ourselves a message: '" + message + "'");
                    consumeFuture.complete(null);
                }
            }
        });

        consumeFuture.get();
        connection.close();
    }
}
