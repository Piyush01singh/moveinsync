package com.moveinsync.mdm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class RootController {

    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard.html";
    }

    @GetMapping("/api-info")
    @ResponseBody
    public ResponseEntity<Map<String, String>> apiInfo() {
        return ResponseEntity.ok(Map.of(
                "service", "MoveInSync MDM Backend",
                "status", "UP",
                "health", "/actuator/health"
        ));
    }
}
