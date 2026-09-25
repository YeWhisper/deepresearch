package com.alibaba.cloud.ai.example.deepresearch.mcp;

import com.alibaba.cloud.ai.example.deepresearch.controller.McpController;
import com.alibaba.cloud.ai.example.deepresearch.service.McpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class McpText {

    private McpController mcpController;

    @Autowired
    private McpService mcpService;

    public McpText() {
    }

    @BeforeEach
    void init() {
        System.out.println(mcpService);
        mcpController = new McpController(mcpService);
    }

    @Test
    public void testMcpText(){
        ResponseEntity<Map<String, Object>> all =  mcpController.getAllMcpServices();
        Map<String, Object> body = all.getBody();
        body.forEach((k,v)->{
            System.out.println("key:" + k + " value:" + v);
        });

        System.out.println("---------------------------------------------");
        System.out.println("---------------------------------------------");
        System.out.println("---------------------------------------------");

        System.out.println(all.toString());
    }
}
