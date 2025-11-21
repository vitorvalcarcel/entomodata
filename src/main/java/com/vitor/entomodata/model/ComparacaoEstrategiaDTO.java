package com.vitor.entomodata.model;

public class ComparacaoEstrategiaDTO {
    private Exemplar antigo;
    private Exemplar novo;

    public ComparacaoEstrategiaDTO(Exemplar antigo, Exemplar novo) {
        this.antigo = antigo;
        this.novo = novo;
    }

    public Exemplar getAntigo() { return antigo; }
    public Exemplar getNovo() { return novo; }
    
    public String getCodigo() {
        return novo != null ? novo.getCod() : (antigo != null ? antigo.getCod() : "");
    }
}