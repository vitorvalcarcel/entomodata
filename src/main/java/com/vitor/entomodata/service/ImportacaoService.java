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
    private ExemplarService exemplarService;
    
    @Autowired
    private ExemplarRepository exemplarRepository;
    
    @Autowired
    private ExcelHelper excelHelper;

    // --- UTILITÁRIOS DE VIEW (Movidos do Controller) ---

    public Map<String, String> getCamposMapeaveis() {
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

    // Limpa e prepara o mapa de colunas vindo do HTML
    public Map<String, String> extrairMapaDeColunas(Map<String, String> params, boolean inverterChaveValor) {
        Map<String, String> mapaLimpo = new LinkedHashMap<>();
        
        // 1. Filtra chaves de controle
        for(Map.Entry<String, String> e : params.entrySet()) {
            String k = e.getKey();
            if(!k.equals("nomeArquivo") && !k.equals("novosSelecionados") && 
               !k.equals("existentesSelecionados") && !k.equals("existentesIds") && 
               !k.equals("acao") && !k.equals("escolhasManual") && !k.startsWith("decisao_") && !k.startsWith("escolha_")) {
                
                if (e.getValue() != null && !e.getValue().isEmpty()) {
                    mapaLimpo.put(k, e.getValue());
                }
            }
        }

        // 2. Inverte se necessário (Para quando vem da tela de Seleção de Colunas)
        // HTML: Key="NomeColunaExcel", Value="cod"  --->  Service quer: Key="cod", Value="NomeColunaExcel"
        if (inverterChaveValor) {
            Map<String, String> mapaInvertido = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : mapaLimpo.entrySet()) {
                mapaInvertido.put(entry.getValue(), entry.getKey());
            }
            return mapaInvertido;
        }

        return mapaLimpo;
    }

    // --- MÉTODOS DE LEITURA ---
    
    public List<String> lerCabecalhos(MultipartFile arquivo) throws IOException {
        return excelHelper.lerCabecalhos(arquivo);
    }

    public List<ColunaExcelDTO> analisarArquivoExcel(MultipartFile arquivo) throws IOException {
        return excelHelper.analisarArquivoComAmostras(arquivo);
    }

    // --- LÓGICA DE NEGÓCIO ---

    public AnaliseBancoDTO analisarConflitosComBanco(List<Exemplar> listaDoExcel) {
        List<Exemplar> novos = new ArrayList<>();
        List<Exemplar> existentes = new ArrayList<>();

        for (Exemplar e : listaDoExcel) {
            if (exemplarRepository.existsById(e.getCod())) {
                existentes.add(e);
            } else {
                novos.add(e);
            }
        }
        return new AnaliseBancoDTO(novos, existentes);
    }

    public void salvarLista(List<Exemplar> lista) {
        exemplarRepository.saveAll(lista);
    }

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
                if(idxCodigo != null) {
                     codigoNoExcel = excelHelper.getValorCelula(row.getCell(idxCodigo)).trim();
                }
                
                if (codigoNoExcel == null || codigoNoExcel.isEmpty()) continue;

                Exemplar exemplar = new Exemplar();
                exemplar.setCod(codigoNoExcel);
                
                preencherExemplarComLinha(exemplar, row, mapaDeColunas, indiceDasColunas);
                
                exemplaresProcessados.add(exemplar);
            }
        }
        return exemplaresProcessados;
    }

    // --- MESCLAGEM INTELIGENTE (Lógica interna da planilha) ---

    public Map<String, Map<String, Set<String>>> executarMesclagemInteligente(String nomeArquivo, Map<String, String> mapaDeColunas, Map<String, List<Integer>> duplicatas) throws IOException {
        Set<Integer> linhasParaIgnorar = new HashSet<>();
        for (List<Integer> linhas : duplicatas.values()) linhasParaIgnorar.addAll(linhas);
        salvarLinhasNaoDuplicadas(nomeArquivo, mapaDeColunas, linhasParaIgnorar);

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
                } else if (valoresDistintos.size() == 1) {
                    preencherCampo(exemplarFinal, campo, valoresDistintos.iterator().next());
                } else {
                    conflitosDesteCodigo.put(campo, valoresDistintos);
                    temConflitoGeral = true;
                }
            }

            if (!temConflitoGeral) {
                exemplarService.salvar(exemplarFinal);
            } else {
                conflitosReais.put(codigo, conflitosDesteCodigo);
            }
        }
        return conflitosReais;
    }

    public void aplicarMesclagemFinal(String nomeArquivo, Map<String, String> mapaDeColunas, Map<String, Map<String, String>> decisoes) throws IOException {
        Map<String, List<Integer>> duplicatas = getMapaOcorrencias(nomeArquivo, mapaDeColunas);
        Map<String, List<OpcaoConflito>> dadosBrutos = detalharConflitos(nomeArquivo, mapaDeColunas, duplicatas);

        for (Map.Entry<String, List<OpcaoConflito>> entry : dadosBrutos.entrySet()) {
            String codigo = entry.getKey();
            List<OpcaoConflito> linhas = entry.getValue();
            
            if (decisoes.containsKey(codigo)) {
                Optional<Exemplar> existente = exemplarRepository.findById(codigo);
                Exemplar exemplarFinal = existente.orElse(new Exemplar());
                exemplarFinal.setCod(codigo);
                
                Map<String, String> decisoesDesteCodigo = decisoes.get(codigo);
                Set<String> todosCampos = new HashSet<>();
                for(OpcaoConflito op : linhas) todosCampos.addAll(op.getDados().keySet());

                for (String campo : todosCampos) {
                    if (decisoesDesteCodigo.containsKey(campo)) {
                         preencherCampo(exemplarFinal, campo, decisoesDesteCodigo.get(campo));
                    } else {
                        for (OpcaoConflito linha : linhas) {
                            String val = linha.getDados().get(campo);
                            if (val != null && !val.trim().isEmpty()) {
                                preencherCampo(exemplarFinal, campo, val);
                                break; 
                            }
                        }
                    }
                }
                exemplarService.salvar(exemplarFinal);
            }
        }
    }

    // --- PRIVADOS ---

    private void salvarLinhasNaoDuplicadas(String nomeArquivo, Map<String, String> mapaDeColunas, Set<Integer> linhasParaIgnorar) throws IOException {
        File arquivo = new File(System.getProperty("java.io.tmpdir"), nomeArquivo);
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
                preencherExemplarComLinha(exemplar, row, mapaDeColunas, indiceDasColunas);
                exemplarService.salvar(exemplar);
            }
        }
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
        Map<String, List<Integer>> apenasDuplicados = mapaOcorrencias.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!apenasDuplicados.isEmpty()) throw new DuplicidadeException(apenasDuplicados);
    }
    
    private void preencherExemplarComLinha(Exemplar exemplar, Row row, Map<String, String> mapaDeColunas, Map<String, Integer> indiceDasColunas) {
        for (Map.Entry<String, String> entrada : mapaDeColunas.entrySet()) {
            String campoSistema = entrada.getKey();
            String colunaExcel = entrada.getValue();
            if (indiceDasColunas.containsKey(colunaExcel)) {
                String valor = excelHelper.getValorCelula(row.getCell(indiceDasColunas.get(colunaExcel)));
                if (!valor.isEmpty()) {
                    preencherCampo(exemplar, campoSistema, valor);
                }
            }
        }
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