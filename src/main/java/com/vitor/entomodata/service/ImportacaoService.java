package com.vitor.entomodata.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.vitor.entomodata.model.Exemplar;
import com.vitor.entomodata.model.OpcaoConflito;
import com.vitor.entomodata.exception.DuplicidadeException;
import com.vitor.entomodata.repository.ExemplarRepository;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ImportacaoService {

    @Autowired
    private ExemplarService exemplarService;
    
    @Autowired
    private ExemplarRepository exemplarRepository;

    private final DataFormatter dataFormatter = new DataFormatter();

    public List<String> lerCabecalhos(MultipartFile arquivo) throws IOException {
        List<String> cabecalhos = new ArrayList<>();

        try (InputStream is = arquivo.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row linhaCabecalho = sheet.getRow(0);

            if (linhaCabecalho != null) {
                for (Cell cell : linhaCabecalho) {
                    cabecalhos.add(cell.getStringCellValue());
                }
            }
        }
        return cabecalhos;
    }

    public void processarImportacao(String nomeArquivo, Map<String, String> mapaDeColunas, boolean verificarDuplicidade, Map<String, Integer> linhasEscolhidas) throws IOException {
        File arquivo = new File(System.getProperty("java.io.tmpdir"), nomeArquivo);
        
        if (verificarDuplicidade) {
            verificarDuplicatasAntesDeSalvar(arquivo, mapaDeColunas);
        }

        try (FileInputStream is = new FileInputStream(arquivo);
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            
            Row linhaCabecalho = sheet.getRow(0);
            Map<String, Integer> indiceDasColunas = new HashMap<>();
            for (Cell cell : linhaCabecalho) {
                indiceDasColunas.put(cell.getStringCellValue(), cell.getColumnIndex());
            }
            
            String nomeColunaCodigo = mapaDeColunas.get("cod");
            Integer idxCodigo = indiceDasColunas.get(nomeColunaCodigo);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                if (linhasEscolhidas != null && idxCodigo != null) {
                    String codigoAtual = getValorCelula(row.getCell(idxCodigo)).trim();
                    if (linhasEscolhidas.containsKey(codigoAtual)) {
                        int linhaPermitida = linhasEscolhidas.get(codigoAtual);
                        if ((i + 1) != linhaPermitida) {
                            continue;
                        }
                    }
                }

                String codigoNoExcel = null;
                if(idxCodigo != null) {
                     codigoNoExcel = getValorCelula(row.getCell(idxCodigo)).trim();
                }
                
                if (codigoNoExcel == null || codigoNoExcel.isEmpty()) continue;

                Optional<Exemplar> existente = exemplarRepository.findById(codigoNoExcel);
                Exemplar exemplar = existente.orElse(new Exemplar());
                exemplar.setCod(codigoNoExcel);
                
                for (Map.Entry<String, String> entrada : mapaDeColunas.entrySet()) {
                    String campoSistema = entrada.getKey();
                    String colunaExcel = entrada.getValue();

                    if (colunaExcel != null && !colunaExcel.isEmpty() && indiceDasColunas.containsKey(colunaExcel)) {
                        int indice = indiceDasColunas.get(colunaExcel);
                        Cell cell = row.getCell(indice);
                        String valor = getValorCelula(cell);
                        
                        if (!valor.isEmpty()) {
                            preencherCampo(exemplar, campoSistema, valor);
                        }
                    }
                }
                
                exemplarService.salvar(exemplar);
            }
        }
    }

    public Map<String, List<OpcaoConflito>> detalharConflitos(String nomeArquivo, Map<String, String> mapaDeColunas, Map<String, List<Integer>> duplicatas) throws IOException {
        File arquivo = new File(System.getProperty("java.io.tmpdir"), nomeArquivo);
        Map<String, List<OpcaoConflito>> detalhes = new LinkedHashMap<>();

        try (FileInputStream is = new FileInputStream(arquivo);
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row linhaCabecalho = sheet.getRow(0);
            
            Map<String, Integer> indiceDasColunas = new HashMap<>();
            for (Cell cell : linhaCabecalho) {
                indiceDasColunas.put(cell.getStringCellValue(), cell.getColumnIndex());
            }

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
                             String val = getValorCelula(row.getCell(indiceDasColunas.get(nomeColExcel)));
                             if(!val.isEmpty()) {
                                 dadosDaLinha.put(campoSistema, val);
                             }
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

            if (opcoes.size() <= 1) {
                divergencias.put(codigo, camposDivergentes);
                continue;
            }

            Set<String> todasAsChaves = opcoes.get(0).getDados().keySet();
            
            for (String chave : todasAsChaves) {
                String valorBase = opcoes.get(0).getDados().get(chave);
                
                for (int i = 1; i < opcoes.size(); i++) {
                    String valorAtual = opcoes.get(i).getDados().get(chave);
                    if (valorBase == null) {
                        if (valorAtual != null) {
                            camposDivergentes.add(chave);
                            break; 
                        }
                    } else if (!valorBase.equals(valorAtual)) {
                        camposDivergentes.add(chave);
                        break;
                    }
                }
            }
            divergencias.put(codigo, camposDivergentes);
        }
        return divergencias;
    }

    private void verificarDuplicatasAntesDeSalvar(File arquivo, Map<String, String> mapaDeColunas) throws IOException {
        String nomeColunaCodigo = mapaDeColunas.get("cod");
        if (nomeColunaCodigo == null || nomeColunaCodigo.isEmpty()) return;

        Map<String, List<Integer>> mapaOcorrencias = new HashMap<>();

        try (FileInputStream is = new FileInputStream(arquivo);
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row linhaCabecalho = sheet.getRow(0);
            
            int indiceCodigo = -1;
            for (Cell cell : linhaCabecalho) {
                if (cell.getStringCellValue().equals(nomeColunaCodigo)) {
                    indiceCodigo = cell.getColumnIndex();
                    break;
                }
            }

            if (indiceCodigo == -1) return;

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell cell = row.getCell(indiceCodigo);
                String codigo = getValorCelula(cell).trim();

                if (!codigo.isEmpty()) {
                    mapaOcorrencias.computeIfAbsent(codigo, k -> new ArrayList<>()).add(i + 1);
                }
            }
        }

        Map<String, List<Integer>> apenasDuplicados = mapaOcorrencias.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (!apenasDuplicados.isEmpty()) {
            throw new DuplicidadeException(apenasDuplicados);
        }
    }

    private String getValorCelula(Cell cell) {
        if (cell == null) return "";
        return dataFormatter.formatCellValue(cell);
    }

    private void preencherCampo(Exemplar e, String campo, String valor) {
        if(valor != null) valor = valor.trim();
        
        switch (campo) {
            case "cod": e.setCod(valor); break;
            case "familia": e.setFamilia(valor); break;
            case "subfamilia": e.setSubfamilia(valor); break;
            case "tribo": e.setTribo(valor); break;
            case "subtribo": e.setSubtribo(valor); break;
            case "genero": e.setGenero(valor); break;
            case "subgenero": e.setSubgenero(valor); break;
            case "especie": e.setEspecie(valor); break;
            case "subespecie": e.setSubespecie(valor); break;
            case "autor": e.setAutor(valor); break;
            case "determinador": e.setDeterminador(valor); break;
            case "sexo": e.setSexo(valor); break;
            case "especieVegetalAssociada": e.setEspecieVegetalAssociada(valor); break;
            case "gaveta": e.setGaveta(valor); break;
            case "caixa": e.setCaixa(valor); break;
            case "pais": e.setPais(valor); break;
            case "estado": e.setEstado(valor); break;
            case "cidade": e.setCidade(valor); break;
            case "localidade": e.setLocalidade(valor); break;
            case "proprietarioDoLocalDeColeta": e.setProprietarioDoLocalDeColeta(valor); break;
            case "bioma": e.setBioma(valor); break;
            case "latitude": e.setLatitude(valor); break;
            case "longitude": e.setLongitude(valor); break;
            case "coletor": e.setColetor(valor); break;
            case "metodoDeAquisicao": e.setMetodoDeAquisicao(valor); break;
            case "data": e.setData(valor); break;
            case "horarioColeta": e.setHorarioColeta(valor); break;
        }
    }
}