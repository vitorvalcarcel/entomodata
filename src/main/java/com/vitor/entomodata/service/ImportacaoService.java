package com.vitor.entomodata.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.vitor.entomodata.helper.ExcelHelper;
import com.vitor.entomodata.model.*;
import com.vitor.entomodata.exception.DuplicidadeException;
import com.vitor.entomodata.repository.ExemplarRepository;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ImportacaoService {
    
    @Autowired
    private ExemplarRepository exemplarRepository;
    
    @Autowired
    private ExcelHelper excelHelper;
    
    @Autowired
    private MapeamentoService mapeamentoService;

    @Autowired
    private MesclagemService mesclagemService;

    // --- UTILITÁRIOS DE VIEW ---

    public Map<String, String> getCamposMapeaveis() {
        return mapeamentoService.getCamposMapeaveis();
    }

    public Map<String, String> extrairMapaDeColunas(Map<String, String> params, boolean inverterChaveValor) {
        Map<String, String> mapaLimpo = new LinkedHashMap<>();
        for(Map.Entry<String, String> e : params.entrySet()) {
            String k = e.getKey();
            if(!k.equals("nomeArquivo") && !k.equals("novosSelecionados") && 
               !k.equals("existentesSelecionados") && !k.equals("existentesIds") && 
               !k.equals("acao") && !k.equals("escolhasManual") && 
               !k.startsWith("decisao_") && !k.startsWith("escolha_")) {
                if (e.getValue() != null && !e.getValue().isEmpty()) mapaLimpo.put(k, e.getValue());
            }
        }
        if (inverterChaveValor) {
            Map<String, String> mapaInvertido = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : mapaLimpo.entrySet()) {
                mapaInvertido.put(entry.getValue(), entry.getKey());
            }
            return mapaInvertido;
        }
        return mapaLimpo;
    }

    // --- DELEGATION ---
    public List<String> lerCabecalhos(MultipartFile arquivo) throws IOException {
        return excelHelper.lerCabecalhos(arquivo);
    }
    public List<ColunaExcelDTO> analisarArquivoExcel(MultipartFile arquivo) throws IOException {
        return excelHelper.analisarArquivoComAmostras(arquivo);
    }
    public AnaliseBancoDTO analisarConflitosComBanco(List<Exemplar> listaDoExcel) {
        List<Exemplar> novos = new ArrayList<>();
        List<Exemplar> existentes = new ArrayList<>();
        for (Exemplar e : listaDoExcel) {
            if (exemplarRepository.existsById(e.getCod())) existentes.add(e);
            else novos.add(e);
        }
        return new AnaliseBancoDTO(novos, existentes);
    }
    public void salvarLista(List<Exemplar> lista) {
        exemplarRepository.saveAll(lista);
    }

    // --- PROCESSAMENTO PADRÃO ---
    public List<Exemplar> processarImportacao(String nomeArquivo, Map<String, String> mapaDeColunas, boolean verificarDuplicidade, Map<String, Integer> linhasEscolhidas) throws IOException {
        File arquivo = new File(System.getProperty("java.io.tmpdir"), nomeArquivo);
        List<Exemplar> exemplaresProcessados = new ArrayList<>();
        
        if (verificarDuplicidade) {
            verificarDuplicatasAntesDeSalvar(arquivo, mapaDeColunas);
        }

        try (FileInputStream is = new FileInputStream(arquivo);
             Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> indiceDasColunas = excelHelper.mapearIndicesColunas(sheet);
            String nomeColunaCodigo = mapaDeColunas.get("cod");
            Integer idxCodigo = indiceDasColunas.get(nomeColunaCodigo);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                if (linhasEscolhidas != null && idxCodigo != null) {
                    String codigoAtual = excelHelper.getValorCelula(row.getCell(idxCodigo)).trim();
                    if (linhasEscolhidas.containsKey(codigoAtual)) {
                        int linhaPermitida = linhasEscolhidas.get(codigoAtual);
                        if ((i + 1) != linhaPermitida) continue;
                    }
                }
                String codigoNoExcel = null;
                if(idxCodigo != null) codigoNoExcel = excelHelper.getValorCelula(row.getCell(idxCodigo)).trim();
                if (codigoNoExcel == null || codigoNoExcel.isEmpty()) continue;

                Exemplar exemplar = new Exemplar();
                exemplar.setCod(codigoNoExcel);
                mapeamentoService.preencherExemplarComLinha(exemplar, row, mapaDeColunas, indiceDasColunas);
                exemplaresProcessados.add(exemplar);
            }
        }
        return exemplaresProcessados;
    }

    // --- DELEGATION PARA MESCLAGEM (COM NOVOS TIPOS DE RETORNO) ---
    
    public ResultadoMesclagemDTO executarMesclagemInteligente(String nomeArquivo, Map<String, String> mapaDeColunas, Map<String, List<Integer>> duplicatas) throws IOException {
        return mesclagemService.executarMesclagemInteligente(nomeArquivo, mapaDeColunas, duplicatas);
    }

    public List<Exemplar> aplicarMesclagemFinal(String nomeArquivo, Map<String, String> mapaDeColunas, Map<String, Map<String, String>> decisoes) throws IOException {
        return mesclagemService.aplicarMesclagemFinal(nomeArquivo, mapaDeColunas, decisoes);
    }
    
    public Map<String, List<OpcaoConflito>> detalharConflitos(String nomeArquivo, Map<String, String> mapaDeColunas, Map<String, List<Integer>> duplicatas) throws IOException {
        return mesclagemService.detalharConflitos(nomeArquivo, mapaDeColunas, duplicatas);
    }
    
    public Map<String, Set<String>> analisarDivergencias(Map<String, List<OpcaoConflito>> detalhes) {
        return mesclagemService.analisarDivergencias(detalhes);
    }

    // --- PRIVADOS ---

    private void verificarDuplicatasAntesDeSalvar(File arquivo, Map<String, String> mapaDeColunas) throws IOException {
        String nomeColunaCodigo = mapaDeColunas.get("cod");
        if (nomeColunaCodigo == null || nomeColunaCodigo.isEmpty()) return;
        Map<String, List<Integer>> mapaOcorrencias = new HashMap<>();
        try (FileInputStream is = new FileInputStream(arquivo); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> indiceDasColunas = excelHelper.mapearIndicesColunas(sheet);
            Integer idxCodigo = indiceDasColunas.get(nomeColunaCodigo);
            if (idxCodigo == null) return;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String codigo = excelHelper.getValorCelula(row.getCell(idxCodigo)).trim();
                if (!codigo.isEmpty()) mapaOcorrencias.computeIfAbsent(codigo, k -> new ArrayList<>()).add(i + 1);
            }
        }
        Map<String, List<Integer>> apenasDuplicados = mapaOcorrencias.entrySet().stream().filter(entry -> entry.getValue().size() > 1).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!apenasDuplicados.isEmpty()) throw new DuplicidadeException(apenasDuplicados);
    }
}