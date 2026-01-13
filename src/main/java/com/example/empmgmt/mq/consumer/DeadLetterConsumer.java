package com.example.empmgmt.mq.consumer;

import com.example.empmgmt.config.ExportMqConfig;
import com.example.empmgmt.domain.ExportTask;
import com.example.empmgmt.mq.dto.ExportTaskMessage;
import com.example.empmgmt.repository.ExportTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 死信队列消费者，处理导出任务的失败消息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterConsumer {

    private final ExportTaskRepository exportTaskRepository;

    /**
     *  监听死信队列
     *  处理死信队列中的导出任务消息
     *  只有重试多次仍然失败的消息才会进入死信队列
     */
    @RabbitListener(queues = ExportMqConfig.DLQ_QUEUE)
    public void handleDeadLetter(ExportTaskMessage message) {
        log.error("【报警】收到死信队列消息: {}", message);

        try{
        // 将数据库任务状态强制标记为FAILED
        ExportTask exportTask = exportTaskRepository.findById(message.getTaskId()).orElse(null);
        if(exportTask!=null) {
            exportTask.setStatus("FAILED");
            exportTask.setErrorMsg("系统繁忙或数据异常，经多次重试仍未成功，请联系管理员处理。");
            exportTask.setUpdatedAt(java.time.LocalDateTime.now());
            exportTaskRepository.save(exportTask);
            log.info("已将任务ID={}的状态标记为FAILED", message.getTaskId());
        }

        //TODO： 发送邮件或钉钉通知管理员
    } catch (Exception e){
            log.error("处理死信队列消息失败，message={}，error={}",message,e.getMessage());
        }
    }
}
