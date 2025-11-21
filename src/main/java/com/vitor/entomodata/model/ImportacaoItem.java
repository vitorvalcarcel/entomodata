package com.vitor.entomodata.model;

import java.io.Serializable;

public class ImportacaoItem implements Serializable {
    
    // O dado que veio do Excel (ou resultado da mesclagem)
    private Exemplar exemplar;
    
    // O dado original do banco (se existir conflito com banco)
    private Exemplar exemplarBanco;

    // Metadados de controle
    private boolean duplicadoNaPlanilha; // Se tem outro igual no mesmo arquivo
    private boolean existeNoBanco;       // Se já existe no DB
    private boolean mesclado;            // Se sofreu alteração via Smart Merge
    
    // Decisão do usuário para este item
    private AcaoImportacao acao; 

    public enum AcaoImportacao {
        SALVAR_NOVO,      // Não existe, só salvar
        IGNORAR,          // Não fazer nada
        SOBRESCREVER,     // Existe, apagar o antigo e por este (Update)
        MESCLAR           // Existe, misturar os dados (Smart Merge DB)
    }

    public ImportacaoItem(Exemplar exemplar) {
        this.exemplar = exemplar;
        this.acao = AcaoImportacao.SALVAR_NOVO; // Padrão
        this.duplicadoNaPlanilha = false;
        this.existeNoBanco = false;
        this.mesclado = false;
    }

    // Getters e Setters
    public Exemplar getExemplar() { return exemplar; }
    public void setExemplar(Exemplar exemplar) { this.exemplar = exemplar; }
    
    public Exemplar getExemplarBanco() { return exemplarBanco; }
    public void setExemplarBanco(Exemplar exemplarBanco) { this.exemplarBanco = exemplarBanco; }
    
    public boolean isDuplicadoNaPlanilha() { return duplicadoNaPlanilha; }
    public void setDuplicadoNaPlanilha(boolean duplicadoNaPlanilha) { this.duplicadoNaPlanilha = duplicadoNaPlanilha; }
    
    public boolean isExisteNoBanco() { return existeNoBanco; }
    public void setExisteNoBanco(boolean existeNoBanco) { this.existeNoBanco = existeNoBanco; }
    
    public boolean isMesclado() { return mesclado; }
    public void setMesclado(boolean mesclado) { this.mesclado = mesclado; }
    
    public AcaoImportacao getAcao() { return acao; }
    public void setAcao(AcaoImportacao acao) { this.acao = acao; }
    
    public String getCodigo() {
        return exemplar != null ? exemplar.getCod() : "";
    }
}