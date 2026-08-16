package com.taut0logy.jmeet.config;

import com.taut0logy.jmeet.job.Amqp;
import com.taut0logy.jmeet.job.JobType;
import com.taut0logy.jmeet.outbox.Outbox;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmqpConfig {

    @Bean
    public CustomExchange jobsExchange() {
        return new CustomExchange(Amqp.JOBS_EXCHANGE, "x-delayed-message", true, false,
                Map.of("x-delayed-type", "direct"));
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(Amqp.DLX_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(Outbox.EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Declarables jobTopology() {
        List<Declarable> declarables = new ArrayList<>();
        Queue deadQueue = new Queue(Amqp.DEAD_QUEUE, true);
        declarables.add(deadQueue);

        for (JobType type : JobType.values()) {
            Queue queue = new Queue(type.queueName(), true, false, false,
                    Map.of("x-dead-letter-exchange", Amqp.DLX_EXCHANGE));
            declarables.add(queue);
            declarables.add(BindingBuilder.bind(queue).to(jobsExchange()).with(type.key()).noargs());
            declarables.add(BindingBuilder.bind(deadQueue).to(deadLetterExchange()).with(type.key()));
        }
        return new Declarables(declarables);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public String[] jobQueueNames() {
        return Arrays.stream(JobType.values()).map(JobType::queueName).toArray(String[]::new);
    }
}
