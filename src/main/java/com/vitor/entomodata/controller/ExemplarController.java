package com.vitor.entomodata.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.vitor.entomodata.model.Exemplar;
import com.vitor.entomodata.service.ExemplarService;

@Controller
public class ExemplarController {

    @Autowired
    private ExemplarService service;

    // Tela Principal (Lista de Exemplares com Paginação)
    @GetMapping("/")
    public String listarExemplares(
            Model model,
            @RequestParam(defaultValue = "0") int page,   // Padrão: página 0 (primeira)
            @RequestParam(defaultValue = "100") int size  // Padrão: 100 itens
    ) {
        // Busca a página de dados
        Page<Exemplar> paginaDeAbelhas = service.buscarTodosPaginado(page, size);
        
        // Envia os dados e informações de controle para o HTML
        model.addAttribute("listaDeAbelhas", paginaDeAbelhas); // A página com os dados
        model.addAttribute("currentPage", page);              // Página atual
        model.addAttribute("pageSize", size);                 // Tamanho atual (para manter selecionado)
        model.addAttribute("totalPages", paginaDeAbelhas.getTotalPages()); // Total de páginas
        model.addAttribute("totalItems", paginaDeAbelhas.getTotalElements()); // Total de itens no banco

        return "index";
    }

    @GetMapping("/novo")
    public String mostrarFormulario(Model model) {
        model.addAttribute("exemplar", new Exemplar());
        return "cadastro";
    }

    @PostMapping("/salvar")
    public String salvarExemplar(Exemplar exemplar) {
        service.salvar(exemplar);
        return "redirect:/";
    }
}