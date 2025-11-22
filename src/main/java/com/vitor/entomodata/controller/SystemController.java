package com.vitor.entomodata.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.concurrent.atomic.AtomicLong;

@Controller
public class SystemController {

    @Autowired
    private ApplicationContext context;

    private final AtomicLong lastHeartbeat = new AtomicLong(System.currentTimeMillis());

    private static final long TIMEOUT_MS = 10000; 
    
    @GetMapping("/sistema/sair")
    public String confirmarSaida() {
        return "sistema-sair";
    }

    @PostMapping("/sistema/desligar")
    public String desligarSistema() {
        executarDesligamento(1000);
        return "sistema-desligado";
    }

    @PostMapping("/sistema/alive")
    @ResponseBody
    public void receberSinalDeVida() {
        lastHeartbeat.set(System.currentTimeMillis());
    }

    @Scheduled(fixedRate = 10000, initialDelay = 30000)
    public void verificarInatividade() {
        long agora = System.currentTimeMillis();
        long ultimoSinal = lastHeartbeat.get();

        if ((agora - ultimoSinal) > TIMEOUT_MS) {
            System.out.println("⚠️ Inatividade detectada (sem sinal do navegador por " + (TIMEOUT_MS/1000) + "s). Encerrando...");
            executarDesligamento(0);
        }
    }

    private void executarDesligamento(long delay) {
        new Thread(() -> {
            try {
                if (delay > 0) Thread.sleep(delay);
                SpringApplication.exit(context, () -> 0);
                System.exit(0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}