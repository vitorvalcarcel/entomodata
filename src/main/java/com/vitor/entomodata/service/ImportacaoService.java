package com.vitor.entomodata.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.vitor.entomodata.model.Exemplar;
import com.vitor.entomodata.exception.DuplicidadeException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ImportacaoService {

    @Autowired
    private ExemplarService exemplarService;

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

    public void processarImportacao(String nomeArquivo, Map<String, String> mapaDeColunas) throws IOException {
        File arquivo = new File(System.getProperty("java.io.tmpdir"), nomeArquivo);
        
        // 1. VALIDAÇÃO: Verifica se há códigos duplicados DENTRO da planilha antes de prosseguir
        verificarDuplicatasAntesDeSalvar(arquivo, mapaDeColunas);

        // 2. PROCESSAMENTO: Se passou da validação, abre o arquivo de novo para salvar
        try (FileInputStream is = new FileInputStream(arquivo);
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            
            // Mapeia colunas
            Row linhaCabecalho = sheet.getRow(0);
            Map<String, Integer> indiceDasColunas = new HashMap<>();
            for (Cell cell : linhaCabecalho) {
                indiceDasColunas.put(cell.getStringCellValue(), cell.getColumnIndex());
            }

            // Salva os dados
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Exemplar exemplar = new Exemplar();
                
                for (Map.Entry<String, String> entrada : mapaDeColunas.entrySet()) {
                    String campoSistema = entrada.getKey();
                    String colunaExcel = entrada.getValue();

                    if (colunaExcel != null && !colunaExcel.isEmpty() && indiceDasColunas.containsKey(colunaExcel)) {
                        int indice = indiceDasColunas.get(colunaExcel);
                        Cell cell = row.getCell(indice);
                        String valor = getValorCelula(cell);
                        preencherCampo(exemplar, campoSistema, valor);
                    }
                }
                
                if (exemplar.getCod() != null && !exemplar.getCod().isEmpty()) {
                    exemplarService.salvar(exemplar);
                }
            }
        }
    }

    // --- NOVO MÉTODO DE VALIDAÇÃO ---
    private void verificarDuplicatasAntesDeSalvar(File arquivo, Map<String, String> mapaDeColunas) throws IOException {
        // Descobre qual é o nome da coluna do Excel que corresponde ao "cod"
        String nomeColunaCodigo = mapaDeColunas.get("cod");
        if (nomeColunaCodigo == null || nomeColunaCodigo.isEmpty()) {
            return; // Se o usuário não mapeou o código, não dá pra validar duplicidade
        }

        Map<String, List<Integer>> mapaOcorrencias = new HashMap<>();

        try (FileInputStream is = new FileInputStream(arquivo);
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row linhaCabecalho = sheet.getRow(0);
            
            // Acha o índice da coluna de código
            int indiceCodigo = -1;
            for (Cell cell : linhaCabecalho) {
                if (cell.getStringCellValue().equals(nomeColunaCodigo)) {
                    indiceCodigo = cell.getColumnIndex();
                    break;
                }
            }

            if (indiceCodigo == -1) return; // Não achou a coluna

            // Varre todas as linhas guardando onde cada código aparece
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell cell = row.getCell(indiceCodigo);
                String codigo = getValorCelula(cell).trim();

                if (!codigo.isEmpty()) {
                    // Se o código ainda não existe no mapa, cria a lista. Adiciona a linha atual (i + 1 para usuário ver começando do 1)
                    mapaOcorrencias.computeIfAbsent(codigo, k -> new ArrayList<>()).add(i + 1);
                }
            }
        }

        // Filtra apenas os que têm mais de 1 ocorrência
        Map<String, List<Integer>> apenasDuplicados = mapaOcorrencias.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        // Se houver duplicatas, PARE TUDO e lance o erro
        if (!apenasDuplicados.isEmpty()) {
            throw new DuplicidadeException(apenasDuplicados);
        }
    }

    private String getValorCelula(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC: return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            default: return "";
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