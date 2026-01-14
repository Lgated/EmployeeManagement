package com.example.empmgmt;

import com.example.empmgmt.domain.ExportTask;
import com.example.empmgmt.mq.consumer.ExportConsumer;
import com.example.empmgmt.mq.dto.EmployeeExportParams;
import com.example.empmgmt.mq.dto.ExportTaskMessage;
import com.example.empmgmt.repository.EmployeeRepository;
import com.example.empmgmt.repository.ExportTaskRepository;
import com.example.empmgmt.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.concurrent.TimeUnit;


import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RedisQueue {
    @InjectMocks
    private ExportConsumer exportConsumer; // 被测试的类

    @Mock
    private ExportTaskRepository exportTaskRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations; // Redis操作句柄

    @BeforeEach
    void setUp() {
        // 让 stringRedisTemplate.opsForValue() 返回我们 mock 的 valueOperations
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("测试防重逻辑：模拟连发5条消息，只有第1条能执行")
    void testHandleExportTask_DuplicateMessages() throws JsonProcessingException {
        // 1. 准备数据
        Long taskId = 10086L;
        String lockKey = "export:task:lock:" + taskId;

        ExportTaskMessage message = new ExportTaskMessage();
        message.setTaskId(taskId);

        // 模拟数据库里存在的任务
        ExportTask task = new ExportTask();
        task.setId(taskId);
        task.setStatus("PENDING");
        task.setTaskType("EMPLOYEE_EXPORT");
        task.setParams("{}"); // 空JSON

        // 2. 关键步骤：Mock Redis 的行为
        // 第1次调用 setIfAbsent 返回 true (获取锁成功)
        // 第2,3,4,5次调用 setIfAbsent 返回 false (获取锁失败)
        when(valueOperations.setIfAbsent(eq(lockKey), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true)  // 第1次
                .thenReturn(false) // 第2次
                .thenReturn(false) // 第3次
                .thenReturn(false) // 第4次
                .thenReturn(false);// 第5次

        // 3. Mock 业务逻辑依赖 (只有第1次通过锁之后才会用到这些)
        // 如果防重失败，后续的调用也会尝试查库，Mockito会因为没有stubbing而报错或返回null
        when(exportTaskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(objectMapper.readValue(anyString(), eq(EmployeeExportParams.class)))
                .thenReturn(new EmployeeExportParams());

        // 注意：你需要把 Consumer 代码里那个 "故意抛出异常" 的测试代码注释掉，
        // 否则这里第1次执行会抛异常。

        // 4. 执行测试：循环调用 5 次消费者方法
        for (int i = 0; i < 5; i++) {
            System.out.println("正在模拟第 " + (i + 1) + " 次消息消费...");
            exportConsumer.handleExportTask(message);
        }

        // 5. 验证结果 (断言)

        // 验证1：Redis锁 确实被尝试获取了 5 次
        verify(valueOperations, times(5)).setIfAbsent(eq(lockKey), anyString(), anyLong(), any(TimeUnit.class));

        // 验证2：数据库查询 (findById) 只执行了 1 次！ (证明后面4次被挡在外面了)
        verify(exportTaskRepository, times(1)).findById(taskId);

        // 验证3：业务逻辑保存 (save) 只执行了 2 次
        // (一次是改成PROCESSING，一次是改成SUCCESS，都属于那唯一的一次成功执行)
        // 或者是根据你的逻辑，验证至少执行了操作
        verify(exportTaskRepository, atLeast(1)).save(any(ExportTask.class));

        System.out.println("测试通过：只有第1次请求穿透了锁，其余4次均被拦截。");
    }
}
