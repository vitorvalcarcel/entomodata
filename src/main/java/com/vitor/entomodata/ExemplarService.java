package com.vitor.entomodata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExemplarService {

    @Autowired
    private ExemplarRepository repository;

    // Método para listar tudo
    public List<Exemplar> buscarTodos() {
        return repository.findAll();
    }

    // Método para salvar
    public void salvar(Exemplar exemplar) {
        // Adicionar validações aqui.
        
        repository.save(exemplar);
    }
    
    // Método para buscar por ID
    public Exemplar buscarPorId(String cod) {
        return repository.findById(cod).orElse(null);
    }
}