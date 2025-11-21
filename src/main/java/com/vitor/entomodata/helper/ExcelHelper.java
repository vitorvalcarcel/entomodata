package com.vitor.entomodata.helper;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.vitor.entomodata.model.ColunaExcelDTO;
import com.vitor.entomodata.model.Exemplar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class ExcelHelper {

    private final DataFormatter dataFormatter = new DataFormatter();

    // === MÉTODOS DE LEITURA (IMPORTAÇÃO) ===

    public List<String> lerCabecalhos(File arquivo) throws IOException {
        List<String> cabecalhos = new ArrayList<>();
        try (FileInputStream is = new FileInputStream(arquivo);
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

    public List<ColunaExcelDTO> analisarArquivoComAmostras(MultipartFile arquivo) throws IOException {
        List<ColunaExcelDTO> colunas = new ArrayList<>();

        try (InputStream is = arquivo.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row linhaCabecalho = sheet.getRow(0);

            if (linhaCabecalho != null) {
                for (Cell cell : linhaCabecalho) {
                    String nomeColuna = cell.getStringCellValue();
                    int indiceColuna = cell.getColumnIndex();
                    
                    List<String> amostras = new ArrayList<>();
                    int linhasEncontradas = 0;
                    for (int i = 1; i <= sheet.getLastRowNum() && linhasEncontradas < 3 && i < 100; i++) {
                        Row rowData = sheet.getRow(i);
                        if (rowData != null) {
                            String valor = getValorCelula(rowData.getCell(indiceColuna)).trim();
                            if (!valor.isEmpty()) {
                                amostras.add(valor);
                                linhasEncontradas++;
                            }
                        }
                    }
                    colunas.add(new ColunaExcelDTO(nomeColuna, amostras));
                }
            }
        }
        return colunas;
    }

    public String getValorCelula(Cell cell) {
        if (cell == null) return "";
        return dataFormatter.formatCellValue(cell);
    }

    public Map<String, Integer> mapearIndicesColunas(Sheet sheet) {
        Row linhaCabecalho = sheet.getRow(0);
        Map<String, Integer> indices = new HashMap<>();
        if (linhaCabecalho != null) {
            for (Cell cell : linhaCabecalho) {
                indices.put(cell.getStringCellValue(), cell.getColumnIndex());
            }
        }
        return indices;
    }

    // === MÉTODOS DE ESCRITA (EXPORTAÇÃO COM METADADOS) ===

    public ByteArrayInputStream gerarPlanilhaExemplares(List<Exemplar> dados, Map<String, String> colunasParaExportar) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            // --- ABA 1: DADOS ---
            Sheet sheetDados = workbook.createSheet("Coleção Entomológica");
            criarAbaDados(workbook, sheetDados, dados, colunasParaExportar);

            // --- ABA 2: METADADOS (RESUMO) ---
            Sheet sheetMeta = workbook.createSheet("Metadados (Resumo)");
            criarAbaMetadados(workbook, sheetMeta, dados);

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());

        } catch (IOException e) {
            throw new RuntimeException("Falha ao exportar dados para Excel: " + e.getMessage());
        }
    }

    private void criarAbaDados(Workbook workbook, Sheet sheet, List<Exemplar> dados, Map<String, String> colunasParaExportar) {
        // Estilos
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font headerFont = workbook.createFont();
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);

        CellStyle stylePar = workbook.createCellStyle();
        stylePar.setBorderBottom(BorderStyle.THIN); stylePar.setBorderTop(BorderStyle.THIN);
        stylePar.setBorderRight(BorderStyle.THIN); stylePar.setBorderLeft(BorderStyle.THIN);

        CellStyle styleImpar = workbook.createCellStyle();
        styleImpar.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        styleImpar.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        styleImpar.setBorderBottom(BorderStyle.THIN); styleImpar.setBorderTop(BorderStyle.THIN);
        styleImpar.setBorderRight(BorderStyle.THIN); styleImpar.setBorderLeft(BorderStyle.THIN);

        // Cabeçalho
        Row headerRow = sheet.createRow(0);
        int colIndex = 0;
        List<String> chavesOrdenadas = new ArrayList<>(colunasParaExportar.keySet());

        for (String key : chavesOrdenadas) {
            Cell cell = headerRow.createCell(colIndex++);
            cell.setCellValue(colunasParaExportar.get(key));
            cell.setCellStyle(headerStyle);
        }

        // Dados
        int rowIndex = 1;
        for (Exemplar exemplar : dados) {
            Row row = sheet.createRow(rowIndex++);
            colIndex = 0;
            CellStyle currentStyle = (rowIndex % 2 == 0) ? styleImpar : stylePar;

            for (String key : chavesOrdenadas) {
                Cell cell = row.createCell(colIndex++);
                String valor = getValorDoCampo(exemplar, key);
                cell.setCellValue(valor);
                cell.setCellStyle(currentStyle);
            }
        }

        // Formatação final
        for (int i = 0; i < chavesOrdenadas.size(); i++) {
            sheet.autoSizeColumn(i);
        }
        if (rowIndex > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, rowIndex - 1, 0, chavesOrdenadas.size() - 1));
        }
        
        // Congelar cabeçalho
        sheet.createFreezePane(0, 1);
    }

    private void criarAbaMetadados(Workbook workbook, Sheet sheet, List<Exemplar> dados) {
        // Cálculos Estatísticos
        long qtdTotal = dados.size();
        long qtdEspecies = dados.stream().map(Exemplar::getEspecie).filter(s -> s != null && !s.trim().isEmpty()).distinct().count();
        long qtdFamilias = dados.stream().map(Exemplar::getFamilia).filter(s -> s != null && !s.trim().isEmpty()).distinct().count();
        long qtdGeneros = dados.stream().map(Exemplar::getGenero).filter(s -> s != null && !s.trim().isEmpty()).distinct().count();
        long qtdGavetas = dados.stream().map(Exemplar::getGaveta).filter(s -> s != null && !s.trim().isEmpty()).distinct().count();
        long qtdCaixas = dados.stream().map(Exemplar::getCaixa).filter(s -> s != null && !s.trim().isEmpty()).distinct().count();
        
        String dataGeracao = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

        // Estilos
        CellStyle labelStyle = workbook.createCellStyle();
        Font fontBold = workbook.createFont();
        fontBold.setBold(true);
        fontBold.setFontHeightInPoints((short) 12);
        labelStyle.setFont(fontBold);
        labelStyle.setAlignment(HorizontalAlignment.RIGHT);
        
        CellStyle valueStyle = workbook.createCellStyle();
        Font fontValue = workbook.createFont();
        fontValue.setFontHeightInPoints((short) 12);
        valueStyle.setFont(fontValue);
        valueStyle.setAlignment(HorizontalAlignment.LEFT);

        CellStyle titleStyle = workbook.createCellStyle();
        Font fontTitle = workbook.createFont();
        fontTitle.setBold(true);
        fontTitle.setFontHeightInPoints((short) 16);
        fontTitle.setColor(IndexedColors.DARK_BLUE.getIndex());
        titleStyle.setFont(fontTitle);

        // Título
        Row rowTitle = sheet.createRow(1);
        Cell cellTitle = rowTitle.createCell(1);
        cellTitle.setCellValue("Relatório de Exportação - EntomoData");
        cellTitle.setCellStyle(titleStyle);

        // Linhas de Dados
        int r = 3;
        adicionarLinhaMetadado(sheet, r++, "Data da Geração:", dataGeracao, labelStyle, valueStyle);
        r++; // Espaço
        adicionarLinhaMetadado(sheet, r++, "Total de Exemplares:", String.valueOf(qtdTotal), labelStyle, valueStyle);
        adicionarLinhaMetadado(sheet, r++, "Total de Espécies:", String.valueOf(qtdEspecies), labelStyle, valueStyle);
        adicionarLinhaMetadado(sheet, r++, "Total de Gêneros:", String.valueOf(qtdGeneros), labelStyle, valueStyle);
        adicionarLinhaMetadado(sheet, r++, "Total de Famílias:", String.valueOf(qtdFamilias), labelStyle, valueStyle);
        r++; // Espaço
        adicionarLinhaMetadado(sheet, r++, "Total de Gavetas:", String.valueOf(qtdGavetas), labelStyle, valueStyle);
        adicionarLinhaMetadado(sheet, r++, "Total de Caixas:", String.valueOf(qtdCaixas), labelStyle, valueStyle);

        // Ajuste de largura
        sheet.setColumnWidth(1, 6000); // Coluna B (Rótulos)
        sheet.setColumnWidth(2, 8000); // Coluna C (Valores)
    }

    private void adicionarLinhaMetadado(Sheet sheet, int rowNum, String label, String value, CellStyle styleLabel, CellStyle styleValue) {
        Row row = sheet.createRow(rowNum);
        
        Cell cellLabel = row.createCell(1);
        cellLabel.setCellValue(label);
        cellLabel.setCellStyle(styleLabel);
        
        Cell cellValue = row.createCell(2);
        cellValue.setCellValue(value);
        cellValue.setCellStyle(styleValue);
    }

    private String getValorDoCampo(Exemplar exemplar, String nomeCampo) {
        try {
            Field field = Exemplar.class.getDeclaredField(nomeCampo);
            field.setAccessible(true);
            Object value = field.get(exemplar);
            return value != null ? value.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }
}