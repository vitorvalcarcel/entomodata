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
import com.vitor.entomodata.model.ColunaExcelDTO;
import com.vitor.entomodata.model.Exemplar;
import com.vitor.entomodata.model.AnaliseBancoDTO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

@Controller
public class ImportacaoController {

    @Autowired
    private ImportacaoService service;

    private Map<String, String> getCamposAmigaveis() {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("cod", "Código (ID)");
        campos.put("especie", "Espécie");
        campos.put("familia", "Família");
        campos.put("subfamilia", "Subfamília");
        campos.put("tribo", "Tribo");
        campos.put("subtribo", "Subtribo");
        campos.put("genero", "Gênero");
        campos.put("subgenero", "Subgênero");
        campos.put("subespecie", "Subespécie");
        campos.put("autor", "Autor");
        campos.put("determinador", "Determinador");
        campos.put("sexo", "Sexo");
        campos.put("especieVegetalAssociada", "Espécie Vegetal Associada");
        campos.put("pais", "País da Coleta");
        campos.put("estado", "Estado da Coleta");
        campos.put("cidade", "Cidade/Município da Coleta");
        campos.put("localidade", "Localidade Específica da Coleta");
        campos.put("proprietarioDoLocalDeColeta", "Proprietário do Local da Coleta");
        campos.put("bioma", "Bioma");
        campos.put("latitude", "Latitude");
        campos.put("longitude", "Longitude");
        campos.put("data", "Data da Coleta");
        campos.put("horarioColeta", "Horário da Coleta");
        campos.put("coletor", "Coletor");
        campos.put("metodoDeAquisicao", "Método de Coleta");
        campos.put("gaveta", "Gaveta");
        campos.put("caixa", "Caixa");
        return campos;
    }

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
            model.addAttribute("camposSistema", getCamposAmigaveis());
            model.addAttribute("nomeArquivoSalvo", nomeArquivo);
            return "importar-mapa"; 
        } catch (IOException e) {
            model.addAttribute("erro", "Erro ao processar arquivo: " + e.getMessage());
            return "importar-upload";
        }
    }

    @PostMapping("/importar/finalizar")
    public String finalizarImportacao(@RequestParam Map<String, String> paramsFormulario, Model model) {
        return fluxoComumDeProcessamento(paramsFormulario, model, true, null);
    }

    @PostMapping("/importar/resolver-conflito")
    public String resolverConflito(@RequestParam String acao, @RequestParam Map<String, String> todosOsParametros, Model model) {
        String nomeArquivo = todosOsParametros.get("nomeArquivo");
        
        if (acao.equals("sobrescrever")) {
            return fluxoComumDeProcessamento(todosOsParametros, model, false, null);
        
        } else if (acao.equals("escolher-manual")) {
             todosOsParametros.remove("nomeArquivo");
             todosOsParametros.remove("acao");
             try {
                service.processarImportacao(nomeArquivo, todosOsParametros, true, null);
                return "redirect:/?sucesso=true"; 
            } catch (DuplicidadeException e) {
                try {
                    Map<String, List<OpcaoConflito>> detalhes = service.detalharConflitos(nomeArquivo, todosOsParametros, e.getDuplicatas());
                    Map<String, Set<String>> divergencias = service.analisarDivergencias(detalhes);
                    model.addAttribute("conflitos", detalhes);
                    model.addAttribute("divergencias", divergencias);
                    model.addAttribute("nomeArquivoSalvo", nomeArquivo);
                    model.addAttribute("mapaAnterior", todosOsParametros);
                    return "importar-manual";
                } catch (IOException ioException) { return "redirect:/importar?erro=" + ioException.getMessage(); }
            } catch (IOException e) { return "redirect:/importar?erro=" + e.getMessage(); }
        
        } else if (acao.equals("smart-merge")) {
            // SMART MERGE
            todosOsParametros.remove("nomeArquivo");
            todosOsParametros.remove("acao");
            
            try {
                // Simula validação para pegar duplicatas
                service.processarImportacao(nomeArquivo, todosOsParametros, true, null);
                return fluxoComumDeProcessamento(todosOsParametros, model, false, null);
                
            } catch (DuplicidadeException e) {
                try {
                    // Executa mesclagem inteligente (já salva os resolvidos e retorna só os conflitos)
                    Map<String, Map<String, Set<String>>> conflitosReais = service.executarMesclagemInteligente(nomeArquivo, todosOsParametros, e.getDuplicatas());
                    
                    if (conflitosReais.isEmpty()) {
                        // Se resolveu tudo, recarrega lista para análise de banco
                        todosOsParametros.put("nomeArquivo", nomeArquivo);
                        return fluxoComumDeProcessamento(todosOsParametros, model, false, null);
                    } else {
                        model.addAttribute("conflitosReais", conflitosReais);
                        model.addAttribute("nomeArquivoSalvo", nomeArquivo);
                        model.addAttribute("mapaAnterior", todosOsParametros);
                        model.addAttribute("nomesAmigaveis", getCamposAmigaveis());
                        return "importar-smart";
                    }
                } catch (IOException io) { return "redirect:/importar?erro=" + io.getMessage(); }
            } catch (IOException io) { return "redirect:/importar?erro=" + io.getMessage(); }
        }
        return "redirect:/importar";
    }

    @PostMapping("/importar/processar-manual")
    public String processarManual(@RequestParam Map<String, String> todosOsParametros, Model model) {
        Map<String, Integer> linhasParaManter = new HashMap<>();
        for (Map.Entry<String, String> entry : todosOsParametros.entrySet()) {
            if (entry.getKey().startsWith("escolha_")) {
                linhasParaManter.put(entry.getKey().replace("escolha_", ""), Integer.parseInt(entry.getValue()));
            }
        }
        return fluxoComumDeProcessamento(todosOsParametros, model, false, linhasParaManter);
    }

    // CORREÇÃO: Garante que o nomeArquivo está lá
    @PostMapping("/importar/processar-smart")
    public String processarSmart(@RequestParam Map<String, String> todosOsParametros, Model model) {
        String nomeArquivo = todosOsParametros.get("nomeArquivo");
        if (nomeArquivo == null) return "redirect:/importar?erro=ArquivoNaoEncontrado";
        todosOsParametros.remove("nomeArquivo");

        Map<String, Map<String, String>> decisoes = new HashMap<>();
        Map<String, String> colunasLimpas = new HashMap<>();

        for (Map.Entry<String, String> entry : todosOsParametros.entrySet()) {
            if (entry.getKey().startsWith("decisao_")) {
                String raw = entry.getKey().replace("decisao_", "");
                int lastUnderscore = raw.lastIndexOf("_");
                if (lastUnderscore > 0) {
                    String codigo = raw.substring(0, lastUnderscore);
                    String campo = raw.substring(lastUnderscore + 1);
                    decisoes.computeIfAbsent(codigo, k -> new HashMap<>()).put(campo, entry.getValue());
                }
            } else {
                colunasLimpas.put(entry.getKey(), entry.getValue());
            }
        }

        try {
            service.aplicarMesclagemFinal(nomeArquivo, colunasLimpas, decisoes);
            colunasLimpas.put("nomeArquivo", nomeArquivo);
            return fluxoComumDeProcessamento(colunasLimpas, model, false, null);
        } catch (IOException e) {
            return "redirect:/importar?erro=" + e.getMessage();
        }
    }

    private String fluxoComumDeProcessamento(Map<String, String> params, Model model, boolean validar, Map<String, Integer> linhas) {
        String nomeArquivo = params.get("nomeArquivo");
        params.remove("nomeArquivo");
        params.remove("acao");
        params.remove("escolhasManual");
        
        Map<String, String> mapaColunas = new LinkedHashMap<>();
        for(Map.Entry<String, String> e : params.entrySet()) {
            if(!e.getKey().startsWith("escolha_") && !e.getKey().startsWith("decisao_")) {
                mapaColunas.put(e.getKey(), e.getValue());
            }
        }
        
        Map<String, String> mapaParaService = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : mapaColunas.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                 mapaParaService.put(entry.getValue(), entry.getKey());
            }
        }
        if (mapaParaService.isEmpty() && !mapaColunas.isEmpty()) mapaParaService = mapaColunas;

        try {
            List<Exemplar> lista = service.processarImportacao(nomeArquivo, mapaParaService, validar, linhas);
            return encaminharParaAnalise(lista, nomeArquivo, mapaParaService, model);
        } catch (DuplicidadeException e) {
            model.addAttribute("duplicatas", e.getDuplicatas());
            model.addAttribute("nomeArquivoSalvo", nomeArquivo);
            model.addAttribute("mapaAnterior", mapaParaService);
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
        Map<String, String> colunasLimpas = new LinkedHashMap<>();
        for(Map.Entry<String, String> e : params.entrySet()) {
            if(!e.getKey().equals("nomeArquivo") && !e.getKey().equals("novosSelecionados") && !e.getKey().equals("existentesSelecionados")) {
                colunasLimpas.put(e.getKey(), e.getValue());
            }
        }

        try {
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
}