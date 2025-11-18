package com.vitor.entomodata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class ExemplarController {

    @Autowired
    private ExemplarService service;

    // Tela Principal (Lista de Exemplares)
    @GetMapping("/")
    public String listarExemplares(Model model) {
        var lista = service.buscarTodos();
        model.addAttribute("listaDeAbelhas", lista);
        return "index";
    }

    // Tela de Cadastro (Formulário Vazio)
    @GetMapping("/novo")
    public String mostrarFormulario(Model model) {
        model.addAttribute("exemplar", new Exemplar());
        return "cadastro";
    }

    // AÇÃO DE SALVAR (Recebe os dados do formulário)
    @PostMapping("/salvar")
    public String salvarExemplar(Exemplar exemplar) {
        service.salvar(exemplar);
        return "redirect:/";
    }
}