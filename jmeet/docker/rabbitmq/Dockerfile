FROM rabbitmq:4.2.9-management

ADD https://github.com/rabbitmq/rabbitmq-delayed-message-exchange/releases/download/v4.2.0/rabbitmq_delayed_message_exchange-4.2.0.ez /opt/rabbitmq/plugins/

RUN chmod 644 /opt/rabbitmq/plugins/rabbitmq_delayed_message_exchange-4.2.0.ez \
    && rabbitmq-plugins enable --offline rabbitmq_stomp rabbitmq_delayed_message_exchange
