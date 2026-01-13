package com.example.empmgmt.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExportMqConfig {

    public static final String EXPORT_EXCHANGE = "export.exchange";
    public static final String EXPORT_QUEUE = "export.queue";
    public static final String EXPORT_ROUTING_KEY = "export.routing";

    // 定义 Exchange, Queue, and Binding

    @Bean
    public DirectExchange exportExchange() {
        return new DirectExchange(EXPORT_EXCHANGE);
    }

    @Bean
    public Queue exportQueue() {
        // durable() 表示队列持久化，重启后不丢失
        return QueueBuilder.durable(EXPORT_QUEUE).build();
    }

    @Bean
    public Binding exportBinding() {
        return BindingBuilder
                .bind(exportQueue()) // 绑定队列
                .to(exportExchange()) // 绑定交换机
                .with(EXPORT_ROUTING_KEY); // 指定路由键
    }


    // 添加这个Bean：配置JSON消息转换器
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
