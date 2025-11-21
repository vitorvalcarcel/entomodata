package com.vitor.entomodata.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.vitor.entomodata.service.ImportacaoService;
import com.vitor.entomodata.helper.CamposHelper;
import com.vitor.entomodata.exception.DuplicidadeException;
import com.vitor.entomodata.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Controller
public class ImportacaoController {

    @Autowired
    private ImportacaoService service;
    
    @Autowired
    private CamposHelper camposHelper;

    @GetMapping("/importar")
    public String telaUpload() { return "importar-upload"; }

    @PostMapping("/importar/upload")
    public String processarUpload(@RequestParam("arquivoExcel") MultipartFile arquivo, Model model) {
        try {
            String nomeArquivo = arquivo.getOriginalFilename();
            Path caminhoTemporario = Paths.get(System.getProperty("java.io.tmpdir"), nomeArquivo);
            Files.copy(arquivo.getInputStream(), caminhoTemporario, StandardCopyOption.REPLACE_EXISTING);
            
            List<ColunaExcelDTO> colunasDoExcel = service.analisarArquivoExcel(arquivo);
            model.addAttribute("colunasExcel", colunasDoExcel);
            model.addAttribute("camposSistema", service.getCamposMapeaveis());
            model.addAttribute("nomeArquivoSalvo", nomeArquivo);
            return "importar-mapa"; 
        } catch (IOException e) {
            model.addAttribute("erro", "Erro: " + e.getMessage());
            return "importar-upload";
        }
    }

    @PostMapping("/importar/finalizar")
    public String finalizarImportacao(@RequestParam Map<String, String> paramsFormulario, Model model) {
        String nomeArquivo = paramsFormulario.get("nomeArquivo");
        Map<String, String> mapaParaService = service.extrairMapaDeColunas(paramsFormulario, true);

        try {
            // Stateful: Carrega para memória
            service.carregarArquivoParaMemoria(nomeArquivo, mapaParaService);
            service.verificarDuplicidadeInterna();
            
            return encaminharParaAnalise(model, nomeArquivo, mapaParaService);
            
        } catch (DuplicidadeException e) {
            model.addAttribute("duplicatas", e.getDuplicatas());
            model.addAttribute("nomeArquivoSalvo", nomeArquivo);
            model.addAttribute("mapaAnterior", mapaParaService);
            return "importar-conflito";
        } catch (IOException e) {
            return "redirect:/importar?erro=" + e.getMessage();
        }
    }

    @PostMapping("/importar/resolver-conflito")
    public String resolverConflito(@RequestParam String acao, @RequestParam Map<String, String> params, Model model) {
        String nomeArquivo = params.get("nomeArquivo");
        Map<String, String> mapaColunas = service.extrairMapaDeColunas(params, false);
        service.resolverDuplicatasInternas(acao, null);
        return encaminharParaAnalise(model, nomeArquivo, mapaColunas);
    }

    // Helper centralizado para análise
    private String encaminharParaAnalise(Model model, String nomeArquivo, Map<String, String> mapaColunas) {
        AnaliseBancoDTO analise = service.analisarConflitoBanco();
        if (analise.getNovos().isEmpty() && analise.getExistentes().isEmpty()) {
            return "redirect:/?msg=NadaParaImportar";
        }
        model.addAttribute("analise", analise);
        model.addAttribute("nomeArquivoSalvo", nomeArquivo);
        model.addAttribute("mapaAnterior", mapaColunas);
        return "importar-analise";
    }

    @PostMapping("/importar/analise-banco")
    public String processarAnaliseBanco(
            @RequestParam Map<String, String> params,
            @RequestParam(value = "novosSelecionados", required = false) List<String> novosIds,
            @RequestParam(value = "existentesSelecionados", required = false) List<String> existentesIds,
            Model model
    ) {
        String nomeArquivo = params.get("nomeArquivo");
        Map<String, String> mapaColunas = service.extrairMapaDeColunas(params, false);

        // 1. Se houver existentes selecionados, vai para a estratégia
        if (existentesIds != null && !existentesIds.isEmpty()) {
            List<ComparacaoEstrategiaDTO> comparacoes = service.getItensParaEstrategia(existentesIds);
            
            model.addAttribute("comparacoes", comparacoes);
            model.addAttribute("nomeArquivoSalvo", nomeArquivo);
            model.addAttribute("mapaAnterior", mapaColunas);
            model.addAttribute("novosIdsPendentes", novosIds); // Passa adiante
            model.addAttribute("camposHelper", camposHelper);
            
            return "importar-estrategia";
        }

        // 2. Se só tem novos, salva e encerra
        service.executarGravacaoFinal(novosIds, null);
        return "redirect:/?msg=ImportacaoConcluida";
    }
    
    @PostMapping("/importar/decisao-estrategia")
    public String processarDecisaoEstrategia(
            @RequestParam Map<String, String> params,
            @RequestParam(value = "novosIdsPendentes", required = false) List<String> novosIds,
            Model model) {
        
        service.aplicarDecisoesEstrategia(params);
        
        // Verifica conflitos finos (Ponto a Ponto)
        Map<String, Map<String, String[]>> conflitosBanco = service.verificarConflitosResolucaoBanco();
        
        // Recupera quais existentes estão sendo processados (com base nas ações do form)
        List<String> existentesIds = new ArrayList<>();
        for(String key : params.keySet()) if(key.startsWith("acao_")) existentesIds.add(key.replace("acao_", ""));

        if (!conflitosBanco.isEmpty()) {
            // Tem conflito real: vai para a tela de resolução fina
            model.addAttribute("conflitosBanco", conflitosBanco);
            model.addAttribute("novosIdsPendentes", novosIds);
            model.addAttribute("existentesIdsPendentes", existentesIds);
            model.addAttribute("camposHelper", service.getCamposMapeaveis());
            
            return "importar-resolucao-banco";
        }
        
        // Sem conflitos: Salva tudo
        service.executarGravacaoFinal(novosIds, existentesIds);
        return "redirect:/?msg=ImportacaoConcluida";
    }

    @PostMapping("/importar/finalizar-resolucao-banco")
    public String finalizarResolucaoBanco(
            @RequestParam Map<String, String> params,
            @RequestParam(value = "novosIdsPendentes", required = false) List<String> novosIds,
            @RequestParam(value = "existentesIdsPendentes", required = false) List<String> existentesIds
            ) {
        
        service.aplicarResolucaoBanco(params);
        service.executarGravacaoFinal(novosIds, existentesIds);
        
        return "redirect:/?msg=ImportacaoConcluida";
    }
    
    // Stubs para endpoints legados (se o frontend ainda chamar)
    @PostMapping("/importar/processar-manual")
    public String processarManual(@RequestParam Map<String, String> params) { return "redirect:/"; }
    @PostMapping("/importar/processar-smart")
    public String processarSmart(@RequestParam Map<String, String> params) { return "redirect:/"; }
}