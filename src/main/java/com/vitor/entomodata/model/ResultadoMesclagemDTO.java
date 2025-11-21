package com.vitor.entomodata.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ResultadoMesclagemDTO {
    // Lista de exemplares já resolvidos e prontos (mas NÃO salvos no banco ainda)
    private List<Exemplar> exemplaresProntos;
    
    // Mapa de conflitos que o sistema não conseguiu resolver sozinho
    private Map<String, Map<String, Set<String>>> conflitosPendentes;

    public ResultadoMesclagemDTO(List<Exemplar> exemplaresProntos, Map<String, Map<String, Set<String>>> conflitosPendentes) {
        this.exemplaresProntos = exemplaresProntos;
        this.conflitosPendentes = conflitosPendentes;
    }

    public List<Exemplar> getExemplaresProntos() {
        return exemplaresProntos;
    }

    public Map<String, Map<String, Set<String>>> getConflitosPendentes() {
        return conflitosPendentes;
    }
    
    // Helper para saber se tudo foi resolvido automaticamente
    public boolean isResolvido() {
        return conflitosPendentes == null || conflitosPendentes.isEmpty();
    }
}