package com.vitor.entomodata.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import com.vitor.entomodata.service.ImportacaoService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Controller
public class ImportacaoController {

    @Autowired
    private ImportacaoService service;

    // Mostra a tela inicial de Upload
    @GetMapping("/importar")
    public String telaUpload() {
        return "importar-upload";
    }

    // Recebe arquivo, SALVA temporariamente e manda para o mapa
    @PostMapping("/importar/upload")
    public String processarUpload(@RequestParam("arquivoExcel") MultipartFile arquivo, Model model) {
        try {
            // Salvar o arquivo numa pasta temporária do sistema
            String nomeArquivo = arquivo.getOriginalFilename();
            Path caminhoTemporario = Paths.get(System.getProperty("java.io.tmpdir"), nomeArquivo);
            Files.copy(arquivo.getInputStream(), caminhoTemporario, StandardCopyOption.REPLACE_EXISTING);

            // Ler os cabeçalhos do Excel
            List<String> colunasDoExcel = service.lerCabecalhos(arquivo);
            
            // Definir quais campos do SISTEMA queremos preencher
            // Estes nomes devem bater com o switch/case no Service
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

    // Recebe o Mapa (De-Para) e finaliza a importação
    @PostMapping("/importar/finalizar")
    public String finalizarImportacao(@RequestParam Map<String, String> todosOsParametros) {
        String nomeArquivo = todosOsParametros.get("nomeArquivo");
        
        // Remove o nomeArquivo do mapa, deixando só os pares "campo -> coluna"
        todosOsParametros.remove("nomeArquivo");
        
        try {
            service.processarImportacao(nomeArquivo, todosOsParametros);
            return "redirect:/?sucesso=true";
        } catch (IOException e) {
            return "redirect:/importar?erro=" + e.getMessage();
        }
    }
}