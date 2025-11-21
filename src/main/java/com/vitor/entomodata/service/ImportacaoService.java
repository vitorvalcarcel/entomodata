package com.vitor.entomodata.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.vitor.entomodata.component.ImportacaoContext;
import com.vitor.entomodata.helper.ExcelHelper;
import com.vitor.entomodata.model.*;
import com.vitor.entomodata.exception.DuplicidadeException;
import com.vitor.entomodata.repository.ExemplarRepository;

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
    
    @Autowired
    private ImportacaoContext context;

    // --- VIEW HELPERS ---
    public Map<String, String> getCamposMapeaveis() { return mapeamentoService.getCamposMapeaveis(); }
    public List<String> lerCabecalhos(MultipartFile arquivo) throws IOException { return excelHelper.lerCabecalhos(arquivo); }
    public List<ColunaExcelDTO> analisarArquivoExcel(MultipartFile arquivo) throws IOException { return excelHelper.analisarArquivoComAmostras(arquivo); }

    public Map<String, String> extrairMapaDeColunas(Map<String, String> params, boolean inverterChaveValor) {
        Map<String, String> mapaLimpo = new LinkedHashMap<>();
        for(Map.Entry<String, String> e : params.entrySet()) {
            String k = e.getKey();
            if(!k.startsWith("nomeArquivo") && !k.startsWith("novos") && !k.startsWith("existentes") && !k.startsWith("acao") && !k.startsWith("escolha") && !k.startsWith("resolucao")) {
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

    // --- ETAPA 1: CARREGAR ---
    public void carregarArquivoParaMemoria(String nomeArquivo, Map<String, String> mapaColunas) throws IOException {
        java.io.File arquivoFisico = new java.io.File(System.getProperty("java.io.tmpdir"), nomeArquivo);
        
        List<ImportacaoItem> itensCarregados = new ArrayList<>();
        
        try (FileInputStream is = new FileInputStream(arquivoFisico); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> indices = excelHelper.mapearIndicesColunas(sheet);
            
            String colCod = mapaColunas.get("cod");
            Integer idxCod = indices.get(colCod);
            
            if (idxCod != null) {
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    String codigo = excelHelper.getValorCelula(row.getCell(idxCod)).trim();
                    if (codigo.isEmpty()) continue;

                    Exemplar e = new Exemplar();
                    e.setCod(codigo);
                    mapeamentoService.preencherExemplarComLinha(e, row, mapaColunas, indices);
                    
                    itensCarregados.add(new ImportacaoItem(e));
                }
            }
        }
        
        context.iniciarNovaImportacao(itensCarregados, nomeArquivo);
        context.setMapaColunasUso(mapaColunas);
    }

    // --- ETAPA 2: DUPLICATAS INTERNAS ---
    public void verificarDuplicidadeInterna() {
        Map<String, List<Integer>> mapaOcorrencias = new HashMap<>();
        List<ImportacaoItem> itens = context.getItens();
        
        for (int i = 0; i < itens.size(); i++) {
            String cod = itens.get(i).getCodigo();
            mapaOcorrencias.computeIfAbsent(cod, k -> new ArrayList<>()).add(i + 1);
        }
        
        Map<String, List<Integer>> apenasDuplicados = mapaOcorrencias.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (!apenasDuplicados.isEmpty()) {
            throw new DuplicidadeException(apenasDuplicados);
        }
    }

    public void resolverDuplicatasInternas(String acao, Map<String, String> manualChoices) {
        Map<String, List<ImportacaoItem>> agrupados = context.getItens().stream()
                .collect(Collectors.groupingBy(ImportacaoItem::getCodigo, LinkedHashMap::new, Collectors.toList()));
        
        List<ImportacaoItem> listaFinal = new ArrayList<>();

        for (Map.Entry<String, List<ImportacaoItem>> entry : agrupados.entrySet()) {
            List<ImportacaoItem> grupo = entry.getValue();
            if (grupo.size() == 1) {
                listaFinal.add(grupo.get(0));
            } else {
                if ("sobrescrever".equals(acao)) {
                    listaFinal.add(grupo.get(grupo.size() - 1));
                } else if ("smart-merge".equals(acao)) {
                    ImportacaoItem itemMesclado = grupo.get(grupo.size() - 1);
                    itemMesclado.setMesclado(true);
                    listaFinal.add(itemMesclado);
                } else {
                    listaFinal.add(grupo.get(grupo.size() - 1));
                }
            }
        }
        context.atualizarListaAposResolucaoInterna(listaFinal);
    }

    // --- ETAPA 3: CONFLITO COM BANCO ---
    public AnaliseBancoDTO analisarConflitoBanco() {
        List<ImportacaoItem> itens = context.getItens();
        List<String> todosIds = itens.stream().map(ImportacaoItem::getCodigo).collect(Collectors.toList());
        
        List<Exemplar> existentesNoBanco = exemplarRepository.findAllById(todosIds);
        Set<String> idsExistentes = existentesNoBanco.stream().map(Exemplar::getCod).collect(Collectors.toSet());
        
        List<Exemplar> listaNovos = new ArrayList<>();
        List<Exemplar> listaExistentes = new ArrayList<>();

        for (ImportacaoItem item : itens) {
            if (idsExistentes.contains(item.getCodigo())) {
                item.setExisteNoBanco(true);
                item.setExemplarBanco(existentesNoBanco.stream().filter(e -> e.getCod().equals(item.getCodigo())).findFirst().orElse(null));
                listaExistentes.add(item.getExemplar());
            } else {
                item.setExisteNoBanco(false);
                item.setAcao(ImportacaoItem.AcaoImportacao.SALVAR_NOVO);
                listaNovos.add(item.getExemplar());
            }
        }
        return new AnaliseBancoDTO(listaNovos, listaExistentes);
    }

    // --- ETAPA 4: ESTRATÉGIA ---
    public List<ComparacaoEstrategiaDTO> getItensParaEstrategia(List<String> idsSelecionados) {
        List<ComparacaoEstrategiaDTO> comps = new ArrayList<>();
        for (ImportacaoItem item : context.getItens()) {
            if (idsSelecionados.contains(item.getCodigo())) {
                comps.add(new ComparacaoEstrategiaDTO(item.getExemplarBanco(), item.getExemplar()));
            }
        }
        return comps;
    }

    public void aplicarDecisoesEstrategia(Map<String, String> decisoes) {
        for (ImportacaoItem item : context.getItens()) {
            String chave = "acao_" + item.getCodigo();
            if (decisoes.containsKey(chave)) {
                String acaoStr = decisoes.get(chave);
                if ("OVERWRITE".equals(acaoStr)) {
                    item.setAcao(ImportacaoItem.AcaoImportacao.SOBRESCREVER);
                } else if ("SMART_MERGE".equals(acaoStr)) {
                    item.setAcao(ImportacaoItem.AcaoImportacao.MESCLAR);
                    Exemplar resultado = mesclagemService.mesclarDoisExemplares(item.getExemplarBanco(), item.getExemplar());
                    item.setExemplar(resultado);
                }
            }
        }
    }

    // --- ETAPA 5: RESOLUÇÃO FINA (PONTO A PONTO) ---
    public Map<String, Map<String, String[]>> verificarConflitosResolucaoBanco() {
        Map<String, Map<String, String[]>> todosConflitos = new HashMap<>();
        
        for (ImportacaoItem item : context.getItens()) {
            if (item.getAcao() == ImportacaoItem.AcaoImportacao.MESCLAR && item.isExisteNoBanco()) {
                Map<String, String[]> conflitosDesteItem = mesclagemService.analisarConflitosPontoAPonto(
                    item.getExemplarBanco(),
                    item.getExemplar()
                );
                if (!conflitosDesteItem.isEmpty()) {
                    todosConflitos.put(item.getCodigo(), conflitosDesteItem);
                }
            }
        }
        return todosConflitos;
    }

    public void aplicarResolucaoBanco(Map<String, String> resolucoes) {
        for (Map.Entry<String, String> entry : resolucoes.entrySet()) {
            String key = entry.getKey();
            String valor = entry.getValue();
            
            if (key.startsWith("resolucao_")) {
                String raw = key.replace("resolucao_", "");
                int lastUnderscore = raw.lastIndexOf("_");
                
                if (lastUnderscore > 0) {
                    String codigo = raw.substring(0, lastUnderscore);
                    String campo = raw.substring(lastUnderscore + 1);
                    
                    ImportacaoItem item = context.getItemPorCodigo(codigo);
                    if (item != null) {
                        mapeamentoService.preencherCampo(item.getExemplar(), campo, valor);
                    }
                }
            }
        }
    }

    // --- ETAPA FINAL: SALVAR ---
    public void executarGravacaoFinal(List<String> idsNovosParaSalvar, List<String> idsExistentesParaProcessar) {
        List<Exemplar> batchSalvar = new ArrayList<>();
        
        for (ImportacaoItem item : context.getItens()) {
            boolean salvar = false;
            
            if (!item.isExisteNoBanco()) {
                if (idsNovosParaSalvar != null && idsNovosParaSalvar.contains(item.getCodigo())) {
                    salvar = true;
                }
            } else {
                // Salva se estiver processado na estratégia e tiver ação
                // Nota: idsExistentesParaProcessar são aqueles que vieram das telas de estratégia
                if (idsExistentesParaProcessar != null && idsExistentesParaProcessar.contains(item.getCodigo())) {
                    if (item.getAcao() == ImportacaoItem.AcaoImportacao.SOBRESCREVER || 
                        item.getAcao() == ImportacaoItem.AcaoImportacao.MESCLAR) {
                        salvar = true;
                    }
                }
            }

            if (salvar) {
                batchSalvar.add(item.getExemplar());
            }
        }
        
        if (!batchSalvar.isEmpty()) {
            exemplarRepository.saveAll(batchSalvar);
        }
        context.limpar();
    }
    
    // Delegates (Mantendo para não quebrar contratos antigos se chamados)
    public ResultadoMesclagemDTO executarMesclagemInteligente(String n, Map<String, String> m, Map<String, List<Integer>> d) throws IOException { return mesclagemService.executarMesclagemInteligente(n, m, d); }
    public List<Exemplar> aplicarMesclagemFinal(String n, Map<String, String> m, Map<String, Map<String, String>> d) throws IOException { return mesclagemService.aplicarMesclagemFinal(n, m, d); }
    public Map<String, List<OpcaoConflito>> detalharConflitos(String n, Map<String, String> m, Map<String, List<Integer>> d) throws IOException { return mesclagemService.detalharConflitos(n, m, d); }
    public Map<String, Set<String>> analisarDivergencias(Map<String, List<OpcaoConflito>> d) { return mesclagemService.analisarDivergencias(d); }
}