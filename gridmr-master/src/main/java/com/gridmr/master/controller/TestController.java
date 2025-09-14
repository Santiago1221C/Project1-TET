package com.gridmr.master.controller;

import org.springframework.web.bind.annotation.*;

/**
 * Controlador de prueba para verificar que Spring está funcionando correctamente
 */
@RestController
@RequestMapping("/api/test")
@CrossOrigin(origins = "*")
public class TestController {
    
    /**
     * Endpoint de prueba simple
     */
    @GetMapping("/status")
    public String getTestStatus() {
        return "✅ Controlador de prueba funcionando correctamente";
    }
}
