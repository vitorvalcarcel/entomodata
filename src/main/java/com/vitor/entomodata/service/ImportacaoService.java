package com.vitor.entomodata.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.vitor.entomodata.model.Exemplar;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImportacaoService {

    @Autowired
    private ExemplarService exemplarService;

    // Abre o Excel e devolve uma lista com os nomes das colunas (Cabeçalho)
    public List<String> lerCabecalhos(MultipartFile arquivo) throws IOException {
        List<String> cabecalhos = new ArrayList<>();

        try (InputStream is = arquivo.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            // Pega a primeira aba (Sheet 0)
            Sheet sheet = workbook.getSheetAt(0);
            
            // Pega a primeira linha (Row 0)
            Row linhaCabecalho = sheet.getRow(0);

            if (linhaCabecalho != null) {
                // Varre todas as células da primeira linha para pegar os nomes
                for (Cell cell : linhaCabecalho) {
                    cabecalhos.add(cell.getStringCellValue());
                }
            }
        }
        return cabecalhos;
    }

    // Processa o arquivo usando o mapa de colunas
    public void processarImportacao(String nomeArquivo, Map<String, String> mapaDeColunas) throws IOException {
        // Abre o arquivo temporário onde salvamos
        File arquivo = new File(System.getProperty("java.io.tmpdir"), nomeArquivo);
        
        try (FileInputStream is = new FileInputStream(arquivo);
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            
            // Mapear o índice das colunas do Excel pelo nome
            // Ex: "Data" é a coluna 0, "Lat" é a coluna 5...
            Row linhaCabecalho = sheet.getRow(0);
            Map<String, Integer> indiceDasColunas = new HashMap<>();
            for (Cell cell : linhaCabecalho) {
                indiceDasColunas.put(cell.getStringCellValue(), cell.getColumnIndex());
            }

            // Iterar pelas linhas de dados (começando da linha 1, pois 0 é cabeçalho)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Exemplar exemplar = new Exemplar();
                
                // Para cada campo do sistema que será preenchido
                for (Map.Entry<String, String> entrada : mapaDeColunas.entrySet()) {
                    String campoSistema = entrada.getKey();
                    String colunaExcel = entrada.getValue();

                    // Se o usuário escolheu uma coluna válida e ela existe no Excel
                    if (colunaExcel != null && !colunaExcel.isEmpty() && indiceDasColunas.containsKey(colunaExcel)) {
                        int indice = indiceDasColunas.get(colunaExcel);
                        Cell cell = row.getCell(indice);
                        String valor = getValorCelula(cell);
                        
                        // Preencher o dado no objeto Exemplar
                        preencherCampo(exemplar, campoSistema, valor);
                    }
                }
                
                // Só salva se tiver pelo menos o Código preenchido
                if (exemplar.getCod() != null && !exemplar.getCod().isEmpty()) {
                    exemplarService.salvar(exemplar);
                }
            }
        }
    }

    // Método auxiliar para pegar valor da célula como String, independente do tipo
    private String getValorCelula(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue();
            case NUMERIC: 
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            default: return "";
        }
    }

    // Método manual para preencher os campos com base no nome
    private void preencherCampo(Exemplar e, String campo, String valor) {
        if(valor != null) valor = valor.trim(); // Remove espaços extras
        
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