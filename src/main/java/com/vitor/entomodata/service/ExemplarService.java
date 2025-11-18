package com.vitor.entomodata.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.vitor.entomodata.model.Exemplar;
import com.vitor.entomodata.repository.ExemplarRepository;

import java.util.List;

@Service
public class ExemplarService {

    @Autowired
    private ExemplarRepository repository;

    // ALTERADO: Agora retorna uma Página (Page) e recebe parâmetros de paginação
    public Page<Exemplar> buscarTodosPaginado(int numeroPagina, int tamanhoPagina) {
        // Cria o objeto que diz ao banco: "Quero a página X com Y itens"
        Pageable paginacao = PageRequest.of(numeroPagina, tamanhoPagina);
        return repository.findAll(paginacao);
    }

    // Mantemos este método caso precise da lista completa em algum lugar (ex: exportar tudo)
    public List<Exemplar> buscarTodos() {
        return repository.findAll();
    }

    public void salvar(Exemplar exemplar) {
        repository.save(exemplar);
    }
    
    public Exemplar buscarPorId(String cod) {
        return repository.findById(cod).orElse(null);
    }
}