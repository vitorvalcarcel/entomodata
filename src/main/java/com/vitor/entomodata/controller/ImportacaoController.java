package com.vitor.entomodata.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.vitor.entomodata.service.ImportacaoService;
import com.vitor.entomodata.exception.DuplicidadeException;
import com.vitor.entomodata.model.OpcaoConflito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class ImportacaoController {

    @Autowired
    private ImportacaoService service;

    @GetMapping("/importar")
    public String telaUpload() {
        return "importar-upload";
    }

    @PostMapping("/importar/upload")
    public String processarUpload(@RequestParam("arquivoExcel") MultipartFile arquivo, Model model) {
        try {
            String nomeArquivo = arquivo.getOriginalFilename();
            Path caminhoTemporario = Paths.get(System.getProperty("java.io.tmpdir"), nomeArquivo);
            Files.copy(arquivo.getInputStream(), caminhoTemporario, StandardCopyOption.REPLACE_EXISTING);

            List<String> colunasDoExcel = service.lerCabecalhos(arquivo);
            
            List<String> camposDoSistema = Arrays.asList(
                "cod", 
                "familia", "subfamilia", "tribo", "subtribo", 
                "genero", "subgenero", "especie", "subespecie", 
                "autor", "determinador", "sexo", "especieVegetalAssociada",
                "gaveta", "caixa",
                "pais", "estado", "cidade", "localidade", "proprietarioDoLocalDeColeta",
                "bioma", "latitude", "longitude",
                "coletor", "metodoDeAquisicao", "data", "horarioColeta"
            );

            model.addAttribute("colunasExcel", colunasDoExcel);
            model.addAttribute("camposSistema", camposDoSistema);
            model.addAttribute("nomeArquivoSalvo", nomeArquivo);
            
            return "importar-mapa"; 
            
        } catch (IOException e) {
            model.addAttribute("erro", "Erro ao processar arquivo: " + e.getMessage());
            return "importar-upload";
        }
    }

    @PostMapping("/importar/finalizar")
    public String finalizarImportacao(@RequestParam Map<String, String> todosOsParametros, Model model) {
        String nomeArquivo = todosOsParametros.get("nomeArquivo");
        todosOsParametros.remove("nomeArquivo");
        
        try {
            service.processarImportacao(nomeArquivo, todosOsParametros, true, null);
            return "redirect:/?sucesso=true";
            
        } catch (DuplicidadeException e) {
            model.addAttribute("duplicatas", e.getDuplicatas());
            model.addAttribute("nomeArquivoSalvo", nomeArquivo);
            model.addAttribute("mapaAnterior", todosOsParametros); 
            return "importar-conflito";
            
        } catch (IOException e) {
            return "redirect:/importar?erro=" + e.getMessage();
        }
    }

    @PostMapping("/importar/resolver-conflito")
    public String resolverConflito(
            @RequestParam String acao,
            @RequestParam Map<String, String> todosOsParametros,
            Model model
    ) {
        String nomeArquivo = todosOsParametros.get("nomeArquivo");
        todosOsParametros.remove("nomeArquivo");
        todosOsParametros.remove("acao");

        try {
            if (acao.equals("sobrescrever")) {
                service.processarImportacao(nomeArquivo, todosOsParametros, false, null);
                return "redirect:/?sucesso=true";
            
            } else if (acao.equals("escolher-manual")) {
                try {
                    service.processarImportacao(nomeArquivo, todosOsParametros, true, null);
                    return "redirect:/?sucesso=true";
                } catch (DuplicidadeException e) {
                    Map<String, List<OpcaoConflito>> detalhes = service.detalharConflitos(nomeArquivo, todosOsParametros, e.getDuplicatas());
                    
                    Map<String, Set<String>> divergencias = service.analisarDivergencias(detalhes);

                    model.addAttribute("conflitos", detalhes);
                    model.addAttribute("divergencias", divergencias);
                    model.addAttribute("nomeArquivoSalvo", nomeArquivo);
                    model.addAttribute("mapaAnterior", todosOsParametros);
                    
                    return "importar-manual";
                }
            
            } else if (acao.equals("smart-merge")) {
                return "redirect:/importar?erro=Funcionalidade_Em_Construcao";
            }
            
            return "redirect:/importar";

        } catch (IOException e) {
            return "redirect:/importar?erro=" + e.getMessage();
        }
    }

    @PostMapping("/importar/processar-manual")
    public String processarManual(
            @RequestParam Map<String, String> todosOsParametros,
            @RequestParam(value = "escolhasManual", required = false) List<String> escolhas 
    ) {
        String nomeArquivo = todosOsParametros.get("nomeArquivo");
        todosOsParametros.remove("nomeArquivo");
        todosOsParametros.remove("escolhasManual");

        java.util.Map<String, Integer> linhasParaManter = new java.util.HashMap<>();
        java.util.Map<String, String> colunasLimpas = new java.util.HashMap<>();
        
        for (Map.Entry<String, String> entry : todosOsParametros.entrySet()) {
            if (entry.getKey().startsWith("escolha_")) {
                String codigo = entry.getKey().replace("escolha_", "");
                int linhaEscolhida = Integer.parseInt(entry.getValue());
                linhasParaManter.put(codigo, linhaEscolhida);
            } else {
                colunasLimpas.put(entry.getKey(), entry.getValue());
            }
        }

        try {
            service.processarImportacao(nomeArquivo, colunasLimpas, false, linhasParaManter);
            return "redirect:/?sucesso=true";
            
        } catch (IOException e) {
            return "redirect:/importar?erro=" + e.getMessage();
        }
    }
}