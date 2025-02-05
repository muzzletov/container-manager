# Use the official RabbitMQ image with management plugin
FROM rabbitmq:3-management

# Copy the definitions file to the container
COPY definitions.json /etc/rabbitmq/definitions.json

# Set environment variables to load the definitions file
ENV RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS "-rabbitmq_management load_definitions \"/etc/rabbitmq/definitions.json\""

# Expose the necessary ports
EXPOSE 5672 15672