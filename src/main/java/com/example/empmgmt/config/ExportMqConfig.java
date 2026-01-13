package com.example.empmgmt.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class ExportMqConfig {

    public static final String EXPORT_EXCHANGE = "export.exchange";
    public static final String EXPORT_QUEUE = "export.queue";
    public static final String EXPORT_ROUTING_KEY = "export.routing";

    // 死信队列
    public static final String DLX_EXCHANGE = "export.dlx.exchange"; // 死信交换机
    public static final String DLQ_QUEUE = "export.dlq.queue";       // 死信队列
    public static final String DLQ_ROUTING_KEY = "export.dlq.routing"; // 死信路由键


    // 定义 Exchange, Queue, and Binding

    @Bean
    public DirectExchange exportExchange() {
        return new DirectExchange(EXPORT_EXCHANGE);
    }

    @Bean
    public Queue exportQueue() {
        // 配置队列参数
        Map<String, Object> args = new HashMap<>();
        // 配置死信队列参数
        args.put("x-dead-letter-exchange", DLX_EXCHANGE); // 指定死信交换机
        args.put("x-dead-letter-routing-key", DLQ_ROUTING_KEY); // 指定死信路由键
        // durable() 表示队列持久化，重启后不丢失
        return QueueBuilder.durable(EXPORT_QUEUE)
                .withArguments(args) // 设置参数
                .build();
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

    // --- 定义死信交换机 ---
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    // --- 定义死信队列 ---
    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    // --- 绑定死信队列到死信交换机 ---
    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(dlqQueue())
                .to(dlxExchange())
                .with(DLQ_ROUTING_KEY);
    }

}
