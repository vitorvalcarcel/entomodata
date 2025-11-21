package com.vitor.entomodata.component;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;
import com.vitor.entomodata.model.ImportacaoItem;
import java.util.*;

@Component
@SessionScope // Um contexto diferente para cada usuário logado
public class ImportacaoContext {

    private List<ImportacaoItem> itens = new ArrayList<>();
    private String nomeArquivoOriginal;
    private Map<String, String> mapaColunasUso;

    public void iniciarNovaImportacao(List<ImportacaoItem> novosItens, String nomeArquivo) {
        this.itens = novosItens;
        this.nomeArquivoOriginal = nomeArquivo;
    }

    public List<ImportacaoItem> getItens() { return itens; }
    
    public ImportacaoItem getItemPorCodigo(String codigo) {
        return itens.stream()
            .filter(i -> i.getCodigo().equals(codigo))
            .findFirst()
            .orElse(null);
    }
    
    // Atualiza a lista mantendo apenas os itens resolvidos (sem duplicatas internas)
    public void atualizarListaAposResolucaoInterna(List<ImportacaoItem> listaLimpa) {
        this.itens = listaLimpa;
    }

    public void limpar() {
        this.itens.clear();
        this.nomeArquivoOriginal = null;
        this.mapaColunasUso = null;
    }

    public String getNomeArquivoOriginal() { return nomeArquivoOriginal; }
    public Map<String, String> getMapaColunasUso() { return mapaColunasUso; }
    public void setMapaColunasUso(Map<String, String> mapa) { this.mapaColunasUso = mapa; }
}