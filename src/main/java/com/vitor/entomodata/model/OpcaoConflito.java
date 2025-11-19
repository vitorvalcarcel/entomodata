package com.vitor.entomodata.model;

import java.util.Map;

public class OpcaoConflito {
    private int linha;
    private Map<String, String> dados;
    public OpcaoConflito(int linha, Map<String, String> dados) {
        this.linha = linha;
        this.dados = dados;
    }

    public int getLinha() {
        return linha;
    }

    public Map<String, String> getDados() {
        return dados;
    }
}