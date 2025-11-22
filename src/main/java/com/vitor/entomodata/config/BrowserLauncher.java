package com.vitor.entomodata.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;

@Component
public class BrowserLauncher {

    @EventListener(ApplicationReadyEvent.class)
    public void launchBrowser() {
        String url = "http://localhost:8080";
        
        System.setProperty("java.awt.headless", "false"); 
        
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("nux") || os.contains("nix")) {
                    new ProcessBuilder("xdg-open", url).start();
                } else if (os.contains("mac")) {
                    new ProcessBuilder("open", url).start();
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Não foi possível abrir o navegador automaticamente.");
            System.out.println("👉 Acesse manualmente: " + url);
        }
    }
}