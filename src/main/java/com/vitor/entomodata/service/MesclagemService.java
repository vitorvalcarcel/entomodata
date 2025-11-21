package com.vitor.entomodata.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.vitor.entomodata.helper.ExcelHelper;
import com.vitor.entomodata.model.Exemplar;
import com.vitor.entomodata.model.OpcaoConflito;
import com.vitor.entomodata.model.ResultadoMesclagemDTO;
import com.vitor.entomodata.repository.ExemplarRepository;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MesclagemService {

    @Autowired
    private ExemplarRepository exemplarRepository;

    @Autowired
    private ExcelHelper excelHelper;

    @Autowired
    private MapeamentoService mapeamentoService;

    // --- MÉTODO 1: Executa análise inicial e retorna DTO ---
    public ResultadoMesclagemDTO executarMesclagemInteligente(String nomeArquivo, Map<String, String> mapaDeColunas, Map<String, List<Integer>> duplicatas) throws IOException {
        // 1. Identifica e prepara as linhas que NÃO são duplicadas (apenas memória)
        Set<Integer> linhasParaIgnorar = new HashSet<>();
        for (List<Integer> linhas : duplicatas.values()) linhasParaIgnorar.addAll(linhas);
        
        List<Exemplar> listaFinal = extrairLinhasNaoDuplicadas(nomeArquivo, mapaDeColunas, linhasParaIgnorar);

        // 2. Processa as duplicatas
        Map<String, List<OpcaoConflito>> dadosBrutos = detalharConflitos(nomeArquivo, mapaDeColunas, duplicatas);
        Map<String, Map<String, Set<String>>> conflitosReais = new HashMap<>();

        for (Map.Entry<String, List<OpcaoConflito>> entry : dadosBrutos.entrySet()) {
            String codigo = entry.getKey();
            List<OpcaoConflito> linhas = entry.getValue();
            
            Optional<Exemplar> existente = exemplarRepository.findById(codigo);
            Exemplar exemplarFinal = existente.orElse(new Exemplar());
            exemplarFinal.setCod(codigo);
            
            Map<String, Set<String>> conflitosDesteCodigo = new HashMap<>();
            boolean temConflitoGeral = false;
            Set<String> todosCampos = new HashSet<>();
            for(OpcaoConflito op : linhas) todosCampos.addAll(op.getDados().keySet());

            for (String campo : todosCampos) {
                Set<String> valoresDistintos = new HashSet<>();
                for (OpcaoConflito linha : linhas) {
                    String val = linha.getDados().get(campo);
                    if (val != null && !val.trim().isEmpty()) valoresDistintos.add(val.trim());
                }
                
                if (valoresDistintos.isEmpty()) {
                    // Nada
                } else if (valoresDistintos.size() == 1) {
                    mapeamentoService.preencherCampo(exemplarFinal, campo, valoresDistintos.iterator().next());
                } else {
                    conflitosDesteCodigo.put(campo, valoresDistintos);
                    temConflitoGeral = true;
                }
            }

            if (!temConflitoGeral) {
                listaFinal.add(exemplarFinal);
            } else {
                conflitosReais.put(codigo, conflitosDesteCodigo);
            }
        }
        
        return new ResultadoMesclagemDTO(listaFinal, conflitosReais);
    }

    // --- MÉTODO 2: Aplica a mesclagem final após decisão do usuário (CORRIGIDO) ---
    public List<Exemplar> aplicarMesclagemFinal(String nomeArquivo, Map<String, String> mapaDeColunas, Map<String, Map<String, String>> decisoes) throws IOException {
        Map<String, List<Integer>> duplicatas = getMapaOcorrencias(nomeArquivo, mapaDeColunas);
        
        // 1. Pega os não duplicados primeiro
        Set<Integer> linhasParaIgnorar = new HashSet<>();
        for (List<Integer> linhas : duplicatas.values()) linhasParaIgnorar.addAll(linhas);
        List<Exemplar> listaFinal = extrairLinhasNaoDuplicadas(nomeArquivo, mapaDeColunas, linhasParaIgnorar);
        
        // 2. Processa TODAS as duplicatas (com ou sem decisão manual)
        Map<String, List<OpcaoConflito>> dadosBrutos = detalharConflitos(nomeArquivo, mapaDeColunas, duplicatas);

        for (Map.Entry<String, List<OpcaoConflito>> entry : dadosBrutos.entrySet()) {
            String codigo = entry.getKey();
            List<OpcaoConflito> linhas = entry.getValue();
            
            Optional<Exemplar> existente = exemplarRepository.findById(codigo);
            Exemplar exemplarFinal = existente.orElse(new Exemplar());
            exemplarFinal.setCod(codigo);
            
            // Pega as decisões para este código (se houver, senão mapa vazio)
            Map<String, String> decisoesDesteCodigo = decisoes.getOrDefault(codigo, Collections.emptyMap());
            
            Set<String> todosCampos = new HashSet<>();
            for(OpcaoConflito op : linhas) todosCampos.addAll(op.getDados().keySet());

            for (String campo : todosCampos) {
                // Se o usuário decidiu manualmente este campo, usa a decisão
                if (decisoesDesteCodigo.containsKey(campo)) {
                     mapeamentoService.preencherCampo(exemplarFinal, campo, decisoesDesteCodigo.get(campo));
                } else {
                    // Senão, aplica a lógica automática (pega valores distintos)
                    // Isso cobre: campos sem conflito E códigos inteiros que não tiveram conflito
                    Set<String> valoresDistintos = new HashSet<>();
                    for (OpcaoConflito linha : linhas) {
                        String val = linha.getDados().get(campo);
                        if (val != null && !val.trim().isEmpty()) valoresDistintos.add(val.trim());
                    }
                    
                    if (!valoresDistintos.isEmpty()) {
                        mapeamentoService.preencherCampo(exemplarFinal, campo, valoresDistintos.iterator().next());
                    }
                }
            }
            
            // IMPORTANTE: Adiciona na lista final SEMPRE (o bug estava aqui, antes filtrava pelo mapa de decisões)
            listaFinal.add(exemplarFinal);
        }
        return listaFinal;
    }

    // --- Métodos auxiliares ---

    private List<Exemplar> extrairLinhasNaoDuplicadas(String nomeArquivo, Map<String, String> mapaDeColunas, Set<Integer> linhasParaIgnorar) throws IOException {
        File arquivo = new File(System.getProperty("java.io.tmpdir"), nomeArquivo);
        List<Exemplar> lista = new ArrayList<>();
        
        try (FileInputStream is = new FileInputStream(arquivo); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> indiceDasColunas = excelHelper.mapearIndicesColunas(sheet);
            String nomeColunaCodigo = mapaDeColunas.get("cod");
            Integer idxCodigo = indiceDasColunas.get(nomeColunaCodigo);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                if (linhasParaIgnorar.contains(i + 1)) continue;
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String codigoNoExcel = null;
                if(idxCodigo != null) codigoNoExcel = excelHelper.getValorCelula(row.getCell(idxCodigo)).trim();
                if (codigoNoExcel == null || codigoNoExcel.isEmpty()) continue;

                Exemplar exemplar = new Exemplar();
                exemplar.setCod(codigoNoExcel);
                mapeamentoService.preencherExemplarComLinha(exemplar, row, mapaDeColunas, indiceDasColunas);
                lista.add(exemplar);
            }
        }
        return lista;
    }

    public Map<String, List<OpcaoConflito>> detalharConflitos(String nomeArquivo, Map<String, String> mapaDeColunas, Map<String, List<Integer>> duplicatas) throws IOException {
        File arquivo = new File(System.getProperty("java.io.tmpdir"), nomeArquivo);
        Map<String, List<OpcaoConflito>> detalhes = new LinkedHashMap<>();
        try (FileInputStream is = new FileInputStream(arquivo); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> indiceDasColunas = excelHelper.mapearIndicesColunas(sheet);

            for (Map.Entry<String, List<Integer>> entry : duplicatas.entrySet()) {
                String codigo = entry.getKey();
                List<Integer> linhas = entry.getValue();
                List<OpcaoConflito> opcoes = new ArrayList<>();

                for (Integer numeroLinha : linhas) {
                    Row row = sheet.getRow(numeroLinha - 1);
                    Map<String, String> dadosDaLinha = new LinkedHashMap<>();
                    for (Map.Entry<String, String> colEntry : mapaDeColunas.entrySet()) {
                        String campoSistema = colEntry.getKey();
                        String nomeColExcel = colEntry.getValue();
                        if (indiceDasColunas.containsKey(nomeColExcel)) {
                             String val = excelHelper.getValorCelula(row.getCell(indiceDasColunas.get(nomeColExcel)));
                             if(!val.isEmpty()) dadosDaLinha.put(campoSistema, val);
                        }
                    }
                    opcoes.add(new OpcaoConflito(numeroLinha, dadosDaLinha));
                }
                detalhes.put(codigo, opcoes);
            }
        }
        return detalhes;
    }

    public Map<String, Set<String>> analisarDivergencias(Map<String, List<OpcaoConflito>> detalhes) {
        Map<String, Set<String>> divergencias = new HashMap<>();
        for (Map.Entry<String, List<OpcaoConflito>> entry : detalhes.entrySet()) {
            String codigo = entry.getKey();
            List<OpcaoConflito> opcoes = entry.getValue();
            Set<String> camposDivergentes = new HashSet<>();
            if (opcoes.size() <= 1) { divergencias.put(codigo, camposDivergentes); continue; }
            Set<String> todasAsChaves = opcoes.get(0).getDados().keySet();
            for (String chave : todasAsChaves) {
                String valorBase = opcoes.get(0).getDados().get(chave);
                for (int i = 1; i < opcoes.size(); i++) {
                    String valorAtual = opcoes.get(i).getDados().get(chave);
                    if ((valorBase == null && valorAtual != null) || (valorBase != null && !valorBase.equals(valorAtual))) {
                        camposDivergentes.add(chave); break;
                    }
                }
            }
            divergencias.put(codigo, camposDivergentes);
        }
        return divergencias;
    }

    private Map<String, List<Integer>> getMapaOcorrencias(String nomeArquivo, Map<String, String> mapaDeColunas) throws IOException {
        File arquivo = new File(System.getProperty("java.io.tmpdir"), nomeArquivo);
        String nomeColunaCodigo = mapaDeColunas.get("cod");
        Map<String, List<Integer>> mapa = new HashMap<>();
        try (FileInputStream is = new FileInputStream(arquivo); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> indiceDasColunas = excelHelper.mapearIndicesColunas(sheet);
            Integer idxCodigo = indiceDasColunas.get(nomeColunaCodigo);
            if (idxCodigo == null) return mapa;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String cod = excelHelper.getValorCelula(row.getCell(idxCodigo)).trim();
                if (!cod.isEmpty()) mapa.computeIfAbsent(cod, k -> new ArrayList<>()).add(i + 1);
            }
        }
        return mapa.entrySet().stream().filter(e -> e.getValue().size() > 1).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}