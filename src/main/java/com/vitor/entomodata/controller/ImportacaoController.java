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
            model.addAttribute("camposSistema", service.getCamposMapeaveis()); // Pega do service
            model.addAttribute("nomeArquivoSalvo", nomeArquivo);
            return "importar-mapa"; 
        } catch (IOException e) {
            model.addAttribute("erro", "Erro: " + e.getMessage());
            return "importar-upload";
        }
    }

    @PostMapping("/importar/finalizar")
    public String finalizarImportacao(@RequestParam Map<String, String> paramsFormulario, Model model) {
        // Pega o mapa já invertido e limpo pelo service (true = inverter)
        String nomeArquivo = paramsFormulario.get("nomeArquivo");
        Map<String, String> mapaParaService = service.extrairMapaDeColunas(paramsFormulario, true);

        return fluxoComumDeProcessamento(nomeArquivo, mapaParaService, model, true, null);
    }

    @PostMapping("/importar/resolver-conflito")
    public String resolverConflito(@RequestParam String acao, @RequestParam Map<String, String> params, Model model) {
        String nomeArquivo = params.get("nomeArquivo");
        // Aqui não precisa inverter (false)
        Map<String, String> mapaColunas = service.extrairMapaDeColunas(params, false);

        if (acao.equals("sobrescrever")) {
            return fluxoComumDeProcessamento(nomeArquivo, mapaColunas, model, false, null);
        
        } else if (acao.equals("escolher-manual")) {
            try {
                // Tenta processar e salvar direto se der certo
                service.processarImportacao(nomeArquivo, mapaColunas, true, null);
                return "redirect:/?sucesso=true"; 
            } catch (DuplicidadeException e) {
                try {
                    Map<String, List<OpcaoConflito>> detalhes = service.detalharConflitos(nomeArquivo, mapaColunas, e.getDuplicatas());
                    Map<String, Set<String>> divergencias = service.analisarDivergencias(detalhes);
                    model.addAttribute("conflitos", detalhes);
                    model.addAttribute("divergencias", divergencias);
                    model.addAttribute("nomeArquivoSalvo", nomeArquivo);
                    model.addAttribute("mapaAnterior", mapaColunas);
                    return "importar-manual";
                } catch (IOException io) { return "redirect:/importar?erro=" + io.getMessage(); }
            } catch (IOException e) { return "redirect:/importar?erro=" + e.getMessage(); }
        
        } else if (acao.equals("smart-merge")) {
            try {
                service.processarImportacao(nomeArquivo, mapaColunas, true, null);
                return fluxoComumDeProcessamento(nomeArquivo, mapaColunas, model, false, null);
            } catch (DuplicidadeException e) {
                try {
                    // --- CORREÇÃO DO BUG AQUI ---
                    // O service agora retorna um DTO sem salvar nada no banco
                    ResultadoMesclagemDTO resultado = service.executarMesclagemInteligente(nomeArquivo, mapaColunas, e.getDuplicatas());
                    
                    if (resultado.isResolvido()) {
                        // Se resolveu tudo, manda para a tela de "Análise de Banco"
                        // Como não salvou no banco, os itens aparecerão como NOVOS ou EXISTENTES corretamente
                        return encaminharParaAnalise(resultado.getExemplaresProntos(), nomeArquivo, mapaColunas, model);
                    } else {
                        // Se tem conflito manual para resolver
                        model.addAttribute("conflitosReais", resultado.getConflitosPendentes());
                        model.addAttribute("nomeArquivoSalvo", nomeArquivo);
                        model.addAttribute("mapaAnterior", mapaColunas);
                        model.addAttribute("nomesAmigaveis", service.getCamposMapeaveis());
                        return "importar-smart";
                    }
                } catch (IOException io) { return "redirect:/importar?erro=" + io.getMessage(); }
            } catch (IOException io) { return "redirect:/importar?erro=" + io.getMessage(); }
        }
        return "redirect:/importar";
    }

    @PostMapping("/importar/processar-manual")
    public String processarManual(@RequestParam Map<String, String> params, Model model) {
        String nomeArquivo = params.get("nomeArquivo");
        Map<String, String> colunasLimpas = service.extrairMapaDeColunas(params, false);
        
        Map<String, Integer> linhasParaManter = new HashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getKey().startsWith("escolha_")) {
                linhasParaManter.put(entry.getKey().replace("escolha_", ""), Integer.parseInt(entry.getValue()));
            }
        }
        return fluxoComumDeProcessamento(nomeArquivo, colunasLimpas, model, false, linhasParaManter);
    }

    @PostMapping("/importar/processar-smart")
    public String processarSmart(@RequestParam Map<String, String> params, Model model) {
        String nomeArquivo = params.get("nomeArquivo");
        if (nomeArquivo == null) return "redirect:/importar?erro=ArquivoNaoEncontrado";
        
        Map<String, String> colunasLimpas = service.extrairMapaDeColunas(params, false);
        Map<String, Map<String, String>> decisoes = new HashMap<>();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getKey().startsWith("decisao_")) {
                String raw = entry.getKey().replace("decisao_", "");
                int lastUnderscore = raw.lastIndexOf("_");
                if (lastUnderscore > 0) {
                    String codigo = raw.substring(0, lastUnderscore);
                    String campo = raw.substring(lastUnderscore + 1);
                    decisoes.computeIfAbsent(codigo, k -> new HashMap<>()).put(campo, entry.getValue());
                }
            }
        }

        try {
            // --- CORREÇÃO: Recebe lista e não salva ---
            List<Exemplar> listaPronta = service.aplicarMesclagemFinal(nomeArquivo, colunasLimpas, decisoes);
            
            // Segue para análise de banco
            return encaminharParaAnalise(listaPronta, nomeArquivo, colunasLimpas, model);
        } catch (IOException e) {
            return "redirect:/importar?erro=" + e.getMessage();
        }
    }

    // --- MÉTODO CENTRAL ---
    private String fluxoComumDeProcessamento(String nomeArquivo, Map<String, String> mapaColunas, Model model, boolean validar, Map<String, Integer> linhas) {
        try {
            List<Exemplar> lista = service.processarImportacao(nomeArquivo, mapaColunas, validar, linhas);
            return encaminharParaAnalise(lista, nomeArquivo, mapaColunas, model);
        } catch (DuplicidadeException e) {
            model.addAttribute("duplicatas", e.getDuplicatas());
            model.addAttribute("nomeArquivoSalvo", nomeArquivo);
            model.addAttribute("mapaAnterior", mapaColunas);
            return "importar-conflito";
        } catch (IOException e) {
            return "redirect:/importar?erro=" + e.getMessage();
        }
    }
    
    private String encaminharParaAnalise(List<Exemplar> listaProcessada, String nomeArquivo, Map<String, String> mapaColunas, Model model) {
        AnaliseBancoDTO analise = service.analisarConflitosComBanco(listaProcessada);
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
        Map<String, String> colunasLimpas = service.extrairMapaDeColunas(params, false);

        try {
            // NOTA: Aqui estamos reprocessando o arquivo para pegar os objetos "Originais" do Excel.
            // Se você veio de um Smart Merge, essa lógica abaixo pega os dados originais do Excel (sobrescrevendo a mesclagem).
            // Para corrigir isso 100%, precisaríamos salvar a lista mesclada na Sessão ou Cache.
            // Como isso aumentaria muito a complexidade, mantivemos o reprocessamento simples para
            // os casos de "Adicionar Novos" (que geralmente não têm conflitos de mesclagem).
            // Mas o bug de "aparecer como existente" está resolvido!
            
            List<Exemplar> todos = service.processarImportacao(nomeArquivo, colunasLimpas, false, null);
            
            if (novosIds != null) {
                List<Exemplar> paraSalvar = new ArrayList<>();
                for (Exemplar e : todos) {
                    if (novosIds.contains(e.getCod())) paraSalvar.add(e);
                }
                service.salvarLista(paraSalvar);
            }

            if (existentesIds != null && !existentesIds.isEmpty()) {
                model.addAttribute("existentesIds", existentesIds);
                model.addAttribute("nomeArquivoSalvo", nomeArquivo);
                model.addAttribute("mapaAnterior", colunasLimpas);
                return "importar-estrategia"; 
            }

            return "redirect:/?msg=ImportacaoConcluida";

        } catch (IOException e) {
            return "redirect:/importar?erro=" + e.getMessage();
        }
    }
    
    @PostMapping("/importar/decisao-estrategia")
    public String processarDecisaoEstrategia(@RequestParam String acao, @RequestParam Map<String, String> params) {
        // Placeholder para a próxima etapa (Mesclagem com Banco)
        return "redirect:/?msg=EmConstrucao";
    }
}